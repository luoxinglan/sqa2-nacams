// 认证接口（后端：韦政治）——接口规范 §3.1。
// 登录即自动签到、登出即自动签出（A-03），由后端在 login/logout 时记录。
import { get, post } from './http'

export const login = (payload) => post('/auth/login', payload)
export const logout = () => post('/auth/logout')
export const getMe = () => get('/auth/me')
