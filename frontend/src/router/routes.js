import { STAFF_VIEW_ROLES } from './access.js'

export const createRoutes = (aiEnabled) => [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/components/Layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据面板', icon: 'DataLine', roles: STAFF_VIEW_ROLES }
      },
      {
        path: '/teacher',
        name: 'Teacher',
        redirect: '/teacher/list',
        meta: { title: '教师画像', icon: 'UserFilled', roles: STAFF_VIEW_ROLES },
        children: [
          {
            path: '/teacher/list',
            name: 'TeacherList',
            component: () => import('@/views/teacher/list.vue'),
            meta: { title: '教师列表' }
          },
          {
            path: '/teacher/detail/:id',
            name: 'TeacherDetail',
            component: () => import('@/views/teacher/detail.vue'),
            meta: { title: '教师详情', hidden: true }
          }
        ]
      },
      {
        path: '/student',
        name: 'Student',
        redirect: '/student/list',
        meta: { title: '学生画像', icon: 'Reading', roles: STAFF_VIEW_ROLES },
        children: [
          {
            path: '/student/list',
            name: 'StudentList',
            component: () => import('@/views/student/list.vue'),
            meta: { title: '学生列表' }
          },
          {
            path: '/student/detail/:id',
            name: 'StudentDetail',
            component: () => import('@/views/student/detail.vue'),
            meta: { title: '学生详情', hidden: true }
          }
        ]
      },
      {
        path: '/mental',
        name: 'Mental',
        redirect: '/mental/overview',
        meta: { title: '心理健康', icon: 'FirstAidKit', roles: STAFF_VIEW_ROLES },
        children: [
          {
            path: '/mental/overview',
            name: 'MentalOverview',
            component: () => import('@/views/mental/overview.vue'),
            meta: { title: '心理概览', roles: STAFF_VIEW_ROLES }
          },
          {
            path: '/mental/questionnaire',
            name: 'MentalQuestionnaire',
            component: () => import('@/views/mental/questionnaire.vue'),
            meta: { title: '问卷管理', roles: STAFF_VIEW_ROLES }
          },
          {
            path: '/mental/questionnaire/design/:id',
            name: 'MentalQuestionnaireDesign',
            component: () => import('@/views/mental/questionnaire-design.vue'),
            meta: { title: '题目设计', hidden: true, roles: STAFF_VIEW_ROLES }
          },
          {
            path: '/mental/questionnaire/result/:id',
            name: 'MentalQuestionnaireResult',
            component: () => import('@/views/mental/questionnaire-result.vue'),
            meta: { title: '完成情况', hidden: true, roles: STAFF_VIEW_ROLES }
          },
          {
            path: '/mental/analysis',
            name: 'MentalAnalysis',
            component: () => import('@/views/mental/analysis.vue'),
            meta: { title: '分析报告', roles: STAFF_VIEW_ROLES }
          }
        ]
      },
      {
        path: '/student-mental',
        name: 'StudentMental',
        redirect: '/student-mental/list',
        meta: { title: '我的问卷', icon: 'EditPen', roles: ['student'] },
        children: [
          {
            path: '/student-mental/list',
            name: 'StudentMentalList',
            component: () => import('@/views/student-mental/list.vue'),
            meta: { title: '问卷列表', roles: ['student'] }
          },
          {
            path: '/student-mental/take/:id',
            name: 'StudentMentalTake',
            component: () => import('@/views/student-mental/take.vue'),
            meta: { title: '填写问卷', hidden: true, roles: ['student'] }
          },
          {
            path: '/student-mental/result/:assessmentId',
            name: 'StudentMentalResult',
            component: () => import('@/views/student-mental/result.vue'),
            meta: { title: '评估结果', hidden: true, roles: ['student'] }
          },
          {
            path: '/student-mental/history',
            name: 'StudentMentalHistory',
            component: () => import('@/views/student-mental/history.vue'),
            meta: { title: '历史评估', hidden: true, roles: ['student'] }
          }
        ]
      },
      ...(aiEnabled
        ? [
            {
              path: '/agent',
              name: 'Agent',
              redirect: '/agent/warning',
              meta: { title: 'AI 预警', icon: 'Bell', roles: STAFF_VIEW_ROLES },
              children: [
                {
                  path: '/agent/warning',
                  name: 'AgentWarning',
                  component: () => import('@/views/agent/Warning.vue'),
                  meta: { title: '预警中心', roles: STAFF_VIEW_ROLES }
                },
                {
                  path: '/agent/report/:id',
                  name: 'AgentReport',
                  component: () => import('@/views/agent/ReportDetail.vue'),
                  meta: { title: '干预报告', hidden: true, roles: STAFF_VIEW_ROLES }
                }
              ]
            }
          ]
        : []),
      {
        path: '/profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '我的画像', icon: 'Avatar' }
      },
      {
        path: '/admin',
        name: 'Admin',
        redirect: '/admin/users',
        meta: { title: '系统管理', icon: 'Setting', roles: ['admin'] },
        children: [
          {
            path: '/admin/users',
            name: 'AdminUsers',
            component: () => import('@/views/admin/users.vue'),
            meta: { title: '用户管理' }
          },
          {
            path: '/admin/roles',
            name: 'AdminRoles',
            component: () => import('@/views/admin/roles.vue'),
            meta: { title: '角色权限' }
          },
          ...(aiEnabled
            ? [
                {
                  path: '/admin/trace',
                  name: 'AdminTrace',
                  component: () => import('@/views/admin/trace.vue'),
                  meta: { title: 'LLM 追踪', icon: 'Connection' }
                }
              ]
            : [])
        ]
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue')
  }
]
