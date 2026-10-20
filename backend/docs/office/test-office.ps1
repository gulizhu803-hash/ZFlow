param([string]$MySqlImage = 'mysql:8.0')
$ErrorActionPreference = 'Stop'
$backendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$testContainer = 'zflow-office-tests-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$testPassword = [Guid]::NewGuid().ToString('N')
$variableNames = @('ZFLOW_OFFICE_TEST_JDBC_URL', 'ZFLOW_OFFICE_TEST_USER', 'ZFLOW_OFFICE_TEST_PASSWORD')
$previous = @{}
foreach ($name in $variableNames) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$created = $false
try {
    Get-Command docker, mvn -ErrorAction Stop | Out-Null
    docker run -d --name $testContainer -p '127.0.0.1::3306' -e "MYSQL_ROOT_PASSWORD=$testPassword" -e MYSQL_DATABASE=zflow_office_test $MySqlImage --default-time-zone=+00:00 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '无法启动独立 MySQL 测试容器' }
    $created = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        docker exec -e "MYSQL_PWD=$testPassword" $testContainer mysqladmin -uroot ping *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'MySQL 测试实例未在 60 秒内就绪' }
    $binding = docker port $testContainer 3306/tcp
    if ($binding -notmatch '^127\.0\.0\.1:(\d+)$') { throw '未找到独立测试端口' }
    $port = $Matches[1]
    $env:ZFLOW_OFFICE_TEST_JDBC_URL = "jdbc:mysql://127.0.0.1:$port/zflow_office_test?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
    $env:ZFLOW_OFFICE_TEST_USER = 'root'
    $env:ZFLOW_OFFICE_TEST_PASSWORD = $testPassword
    Push-Location $backendRoot
    try {
        mvn -pl zflow-app -am test
        if ($LASTEXITCODE -ne 0) { throw '办公后端测试失败，请查看 zflow-app/target/surefire-reports' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $variableNames) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    if ($created -and $testContainer -match '^zflow-office-tests-[a-f0-9]{8}$') {
        docker rm -f -v $testContainer | Out-Null
    }
}
