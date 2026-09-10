import { createRouter, createWebHistory } from 'vue-router';
const Chat = () => import('../components/Chat.vue');
const Work = () => import('../components/Work.vue');
const Welcome = () => import('../components/Welcome.vue');
const Studio = () => import('../components/Studio.vue');
const Plaza = () => import('../components/Plaza.vue');
const Repository = () => import('../components/Repository.vue');
const Setting = () => import('../components/Setting.vue');
const Template = () => import('../components/Template.vue');
const Detail = () => import('../components/Detail.vue');
const Auth = () => import('../components/Auth.vue');
const AdminAuth = () => import('../components/AdminAuth.vue');
const AdminTable = () => import('../components/AdminTable.vue');
const AdminConfig = () => import('../components/AdminConfig.vue');
const AdminFlow = () => import('../components/AdminFlow.vue');
const AdminCanvas = () => import('../components/AdminCanvas.vue');
const AdminSession = () => import('../components/AdminSession.vue');
const AdminDashboard = () => import('../components/AdminDashboard.vue');
const NotFound = () => import('../components/NotFound.vue');
import { getStoredAuth } from './pinia';

const routes = [
    ...(import.meta.env.DEV ? [
        { path: '/preview', redirect: '/preview/welcome', meta: { preview: true } },
        { path: '/preview/welcome', component: Welcome, meta: { preview: true } },
        { path: '/preview/chat', component: Chat, meta: { preview: true } },
        { path: '/preview/work', component: Work, meta: { preview: true } }
    ] : []),
    {
        path: '/',
        redirect: '/welcome'
    },
    {
        path: '/chat',
        name: 'chat',
        component: Chat
    },
    {
        path: '/welcome',
        name: 'welcome',
        component: Welcome
    },
    {
        path: '/work',
        name: 'work',
        component: Work
    },
    {
        path: '/studio',
        name: 'studio',
        component: Studio
    },
    {
        path: '/plaza',
        name: 'plaza',
        component: Plaza
    },
    {
        path: '/repository',
        name: 'repository',
        component: Repository
    },
    {
        path: '/template/:templateId',
        name: 'template',
        component: Template
    },
    {
        path: '/detail/:agentId',
        name: 'detail',
        component: Detail
    },
    {
        path: '/setting',
        name: 'setting',
        component: Setting
    },
    {
        path: '/login',
        name: 'login',
        component: Auth,
        meta: {
            hideSidebar: true
        }
    },
    {
        path: '/register',
        name: 'register',
        component: Auth,
        meta: {
            hideSidebar: true
        }
    },
    {
        path: '/admin/login',
        name: 'admin-login',
        component: AdminAuth,
        meta: {
            hideSidebar: true
        }
    },
    {
        path: '/admin',
        redirect: '/admin/dashboard',
        meta: {
            requiresAuth: true,
            requiresAdmin: true,
            hideSidebar: true
        }
    },
    {
        path: '/admin/dashboard',
        name: 'admin-dashboard',
        component: AdminDashboard,
        meta: {
            requiresAuth: true,
            requiresAdmin: true,
            hideSidebar: true
        }
    },
    {
        path: '/admin/config',
        name: 'admin-config',
        component: AdminConfig,
        meta: {
            requiresAuth: true,
            requiresAdmin: true,
            hideSidebar: true
        }
    },
    {
        path: '/admin/flow',
        name: 'admin-flow',
        component: AdminFlow,
        meta: {
            requiresAuth: true,
            requiresAdmin: true,
            hideSidebar: true
        }
    },
    {
        path: '/admin/canvas',
        name: 'admin-canvas',
        component: AdminCanvas,
        meta: {
            requiresAuth: true,
            requiresAdmin: true,
            hideSidebar: true
        }
    },
    {
        path: '/admin/session',
        name: 'admin-session',
        component: AdminSession,
        meta: {
            requiresAuth: true,
            requiresAdmin: true,
            hideSidebar: true
        }
    },
    {
        path: '/admin/:module',
        name: 'admin',
        component: AdminTable,
        meta: {
            requiresAuth: true,
            requiresAdmin: true,
            hideSidebar: true
        }
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: NotFound,
        meta: {
            hideSidebar: true
        }
    }
];

const router = createRouter({
    history: createWebHistory('/miniagent/'),
    routes
});

router.beforeEach((to, from, next) => {
    if (import.meta.env.DEV && to.meta?.preview) {
        document.title = '知流 ZFlow · 界面预览';
        next();
        return;
    }
    const auth = getStoredAuth();
    const isAdminRoute = to.path.startsWith('/admin');
    document.title = isAdminRoute ? '知流 ZFlow 后台管理' : '知流 ZFlow';

    // Global auth gate: if not logged in, allow only the login pages.
    if (!auth.token) {
        const allow = to.path === '/login' || to.path === '/register' || to.path === '/admin/login';
        if (!allow) {
            next({ path: '/login', replace: true });
            return;
        }
    }

    if (to.meta?.requiresAdmin && auth?.user?.role !== 'admin') {
        next({ path: '/login', replace: true });
        return;
    }
    next();
});

export default router;
