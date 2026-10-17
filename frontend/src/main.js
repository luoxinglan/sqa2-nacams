import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './styles.css'

import HomePage from './views/HomePage.vue'
import NewsPage from './views/NewsPage.vue'
import NewsDetailPage from './views/NewsDetailPage.vue'
import ContentPage from './views/ContentPage.vue'
import PeoplePage from './views/PeoplePage.vue'
import PersonDetailPage from './views/PersonDetailPage.vue'

const routes = [
  { path: '/', component: HomePage },
  { path: '/news', component: NewsPage },
  { path: '/news/:id', component: NewsDetailPage },
  { path: '/teaching', component: ContentPage, props: { mode: 'teaching' } },
  { path: '/research', component: ContentPage, props: { mode: 'research' } },
  { path: '/achievements', component: ContentPage, props: { mode: 'achievements' } },
  { path: '/admissions', component: ContentPage, props: { mode: 'admissions' } },
  { path: '/cooperations', component: ContentPage, props: { mode: 'cooperations' } },
  { path: '/teachers', component: PeoplePage, props: { kind: 'teachers' } },
  { path: '/teachers/:id', component: PersonDetailPage, props: { kind: 'teachers' } },
  { path: '/students', component: PeoplePage, props: { kind: 'students' } },
  { path: '/students/:id', component: PersonDetailPage, props: { kind: 'students' } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

createApp(App).use(createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })).mount('#app')
