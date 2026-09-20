import { describe,expect,it } from 'vitest'
import { avatarInitial, maskedDingTalkId } from './display'
describe('avatarInitial',()=>{it('uses the first character',()=>{expect(avatarInitial('小贝老师')).toBe('小');expect(avatarInitial('玲')).toBe('玲');expect(avatarInitial('Admin')).toBe('A');expect(avatarInitial('')).toBe('')})})
describe('maskedDingTalkId',()=>{it('only exposes the first six characters',()=>{expect(maskedDingTalkId('01474134493538778794')).toBe('@014741**');expect(maskedDingTalkId('@abcdefghi')).toBe('@abcdef**');expect(maskedDingTalkId()).toBe('')})})
