export const unwrapWorkspaceData = (response) => {
    if (response && Object.prototype.hasOwnProperty.call(response, 'code')) {
        if (response.code !== 200) throw new Error(response.info || '操作失败');
        return response.data;
    }
    return response?.data ?? response;
};

export const mapWorkspaceSession = (session) => ({
    sessionId: session.sessionId,
    sessionUser: session.userName || session.sessionUser || '',
    title: session.sessionTitle || session.title || '新会话',
    sessionType: String(session.sessionType || session.type || '').toLowerCase(),
    createdAt: Date.parse(session.createTime) || Date.now(),
    messages: [], cards: []
});

export const workspacePath = (path, preview = false) => {
    if (!preview) return path;
    return ['/welcome', '/chat', '/work'].includes(path) ? `/preview${path}` : '/login';
};
