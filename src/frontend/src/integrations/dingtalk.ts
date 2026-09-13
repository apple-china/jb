export class DingTalkClientError extends Error {
  constructor(public code:string,message:string){super(message)}
}

declare global {
  interface Window {
    dd?:{requestAuthCode:(options:{clientId:string;corpId:string;success:(result:{code?:string})=>void;fail:(error:unknown)=>void})=>void}
  }
}

export async function requestDingTalkAuthCode(){
  if(!window.dd?.requestAuthCode)throw new DingTalkClientError('NOT_IN_DINGTALK','请在钉钉内打开后使用免登。')
  const clientId=import.meta.env.VITE_DINGTALK_CLIENT_ID?.trim()
  const configuredCorpId=import.meta.env.VITE_DINGTALK_CORP_ID?.trim()
  const urlCorpId=new URLSearchParams(window.location.search).get('corpid')?.trim()
  if(!clientId||(!configuredCorpId&&!urlCorpId))throw new DingTalkClientError('DINGTALK_NOT_CONFIGURED','钉钉免登尚未配置，请联系管理员。')
  if(configuredCorpId&&urlCorpId&&configuredCorpId!==urlCorpId)throw new DingTalkClientError('DINGTALK_CORP_MISMATCH','当前钉钉企业与系统配置不一致。')
  const corpId=configuredCorpId||urlCorpId!
  const authCode=await new Promise<string>((resolve,reject)=>window.dd!.requestAuthCode({
    clientId,corpId,
    success:result=>result.code?resolve(result.code):reject(new DingTalkClientError('DINGTALK_CODE_EMPTY','未获取到免登码')),
    fail:()=>reject(new DingTalkClientError('DINGTALK_AUTH_FAILED','钉钉免登失败，请重试。')),
  }))
  return {authCode,corpId}
}
