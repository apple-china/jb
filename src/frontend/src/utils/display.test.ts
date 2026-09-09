import { describe,expect,it } from 'vitest'
import { avatarInitial } from './display'
describe('avatarInitial',()=>{it('uses the first character',()=>{expect(avatarInitial('小贝老师')).toBe('小');expect(avatarInitial('玲')).toBe('玲');expect(avatarInitial('Admin')).toBe('A');expect(avatarInitial('')).toBe('')})})
