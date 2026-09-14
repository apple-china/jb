import * as dingTalkSdk from 'dingtalk-jsapi'

export class DingTalkClientError extends Error {
  constructor(public code:string,message:string){super(message)}
}

type InjectedDingTalkApi = {
  requestAuthCode:(options:{
    clientId:string
    corpId:string
    success:(result:{code?:string})=>void
    fail:(error:unknown)=>void
  })=>void
}

declare global {
  interface Window {
    dd?:InjectedDingTalkApi
  }
}

export function currentDingTalkCorpId(){
  const fromUrl=new URLSearchParams(window.location.search).get('corpid')?.trim()
  const configured=import.meta.env.VITE_DINGTALK_CORP_ID?.trim()
  return fromUrl||configured||'CORPID'
}

export function missingAuthCodeMessage(corpId=currentDingTalkCorpId(),now=new Date()){
  const part=(value:number)=>String(value).padStart(2,'0')
  return `${part(now.getHours())}:${part(now.getMinutes())}:${part(now.getSeconds())} 未获取到免登码:${corpId}`
}

async function requestFromInjectedApi(api:InjectedDingTalkApi,clientId:string,corpId:string){
  return new Promise<string>((resolve,reject)=>api.requestAuthCode({
    clientId,
    corpId,
    success:result=>result.code
      ? resolve(result.code)
      : reject(new DingTalkClientError('DINGTALK_CODE_EMPTY','未获取到免登码')),
    fail:()=>reject(new DingTalkClientError('DINGTALK_AUTH_FAILED','钉钉免登失败，请重试。')),
  }))
}

export async function requestDingTalkAuthCode(){
  const injectedApi=window.dd?.requestAuthCode?window.dd:undefined
  if(!injectedApi&&dingTalkSdk.env.platform==='notInDingTalk'){
    throw new DingTalkClientError('NOT_IN_DINGTALK','请在钉钉内打开后使用免登。')
  }

  const clientId=import.meta.env.VITE_DINGTALK_CLIENT_ID?.trim()
  const configuredCorpId=import.meta.env.VITE_DINGTALK_CORP_ID?.trim()
  const urlCorpId=new URLSearchParams(window.location.search).get('corpid')?.trim()
  if(!clientId||(!configuredCorpId&&!urlCorpId)){
    throw new DingTalkClientError('DINGTALK_NOT_CONFIGURED','钉钉免登尚未配置，请联系管理员。')
  }
  if(configuredCorpId&&urlCorpId&&configuredCorpId!==urlCorpId){
    throw new DingTalkClientError('DINGTALK_CORP_MISMATCH','当前钉钉企业与系统配置不一致。')
  }
  const corpId=configuredCorpId||urlCorpId!

  if(injectedApi){
    return {authCode:await requestFromInjectedApi(injectedApi,clientId,corpId),corpId}
  }

  try{
    const result=await dingTalkSdk.requestAuthCode({clientId,corpId})
    if(!result.code)throw new DingTalkClientError('DINGTALK_CODE_EMPTY','未获取到免登码')
    return {authCode:result.code,corpId}
  }catch(error){
    if(error instanceof DingTalkClientError)throw error
    throw new DingTalkClientError('DINGTALK_AUTH_FAILED','钉钉免登失败，请重试。')
  }
}
