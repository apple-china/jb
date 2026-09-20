export function avatarInitial(name?:string){
  const characters=Array.from(name?.trim()??'')
  return characters[0]??''
}

export function maskedDingTalkId(value?:string){
  const normalized=(value??'').trim().replace(/^@/,'')
  return normalized ? `@${normalized.slice(0,6)}**` : ''
}
