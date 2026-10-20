package com.zgl.zflow.infrastructure.office;

import com.zgl.zflow.domain.office.gateway.IArtifactStorage;
import com.zgl.zflow.domain.office.model.OfficeJson;
import com.zgl.zflow.types.exception.OfficeException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

public class LocalArtifactStorage implements IArtifactStorage {
    private final Path root;
    private final int maxBytes;
    public LocalArtifactStorage(String storageRoot,int maxBytes) {
        if(storageRoot==null||storageRoot.isBlank()) throw new IllegalArgumentException("启用办公模块需要 zflow.office.storage-root");
        this.maxBytes=maxBytes;
        try {
            Path requested=Path.of(storageRoot).toAbsolutePath().normalize();
            noLinks(requested); Files.createDirectories(requested); noLinks(requested);
            root=requested.toRealPath();
            if(!Files.isWritable(root))throw new IOException("Storage is not writable");
            Files.createDirectories(root.resolve("tmp"));
        } catch(IOException e) {throw new IllegalStateException("办公文件目录不可用",e);}
    }
    @Override public StoredFile writeImmutable(long ownerId,String artifactId,String revisionId,byte[] content) {
        if(ownerId<=0||!artifactId.matches("art_[a-f0-9]{32}")||!revisionId.matches("rev_[a-f0-9]{32}"))throw new IllegalArgumentException("Invalid server identity");
        if(content.length>maxBytes)throw new OfficeException(413,"ARTIFACT_CONTENT_TOO_LARGE","文件超过大小限制");
        Path temporary=null;
        try {
            String key=ownerId+"/"+artifactId+"/"+revisionId+"/content.drawio";
            Path destination=resolve(key);
            Files.createDirectories(destination.getParent().getParent());
            Files.createDirectory(destination.getParent());
            noLinks(destination.getParent());
            if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS))throw new IOException("Immutable object exists");
            temporary=Files.createTempFile(resolve("tmp"),"office-",".part");
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                ByteBuffer buffer=ByteBuffer.wrap(content); while(buffer.hasRemaining())channel.write(buffer); channel.force(true);
            }
            // The revision directory is unique. Never replace an existing immutable file.
            try {Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported) {Files.move(temporary,destination);}
            return new StoredFile(key,OfficeJson.hash(content),content.length);
        } catch(IOException e) {throw unavailable();}
        finally { if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){} }
    }
    @Override public byte[] readVerified(String key,String hash,long size) {
        try {
            Path path=resolve(key);
            if(size<0||size>maxBytes||Files.size(path)!=size)throw new IOException("Size mismatch");
            byte[] content;
            try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){content=in.readNBytes(maxBytes+1);}
            if(content.length!=size||!OfficeJson.hash(content).equals(hash))throw new IOException("Hash mismatch");
            return content;
        } catch(IOException|IllegalArgumentException e) {throw unavailable();}
    }
    @Override public long countOrphans(Set<String> referencedKeys) {
        try(var paths=Files.walk(root)) {
            Instant cutoff=Instant.now().minus(24,ChronoUnit.HOURS);
            return paths.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).filter(p->{
                try{return Files.getLastModifiedTime(p,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)&&
                        !referencedKeys.contains(root.relativize(p).toString().replace('\\','/'));}
                catch(IOException e){return false;}
            }).count();
        }catch(IOException e){throw unavailable();}
    }
    private Path resolve(String key)throws IOException {
        Path path=root.resolve(key).normalize();
        if(!path.startsWith(root)||Path.of(key).isAbsolute())throw new IOException("Path escape");
        noLinks(path); return path;
    }
    private static void noLinks(Path path)throws IOException {
        for(Path p=path;p!=null;p=p.getParent()) {
            if(Files.isSymbolicLink(p))throw new IOException("Symbolic links are not allowed");
            if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&!p.toRealPath().equals(p.toAbsolutePath().normalize()))
                throw new IOException("Linked paths are not allowed");
        }
    }
    private static OfficeException unavailable(){return new OfficeException(503,"ARTIFACT_FILE_UNAVAILABLE","办公文件暂不可读写，请稍后重试");}
}
