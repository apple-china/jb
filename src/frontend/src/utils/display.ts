export function avatarInitial(name?:string){
  const characters=Array.from(name?.trim()??'')
  return characters[0]??''
}
