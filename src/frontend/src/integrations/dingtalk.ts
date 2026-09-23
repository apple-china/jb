import * as dingTalkSdk from 'dingtalk-jsapi'

export class DingTalkClientError extends Error {
  constructor(public code:string,message:string){super(message)}
}

export const DINGTALK_AUTH_TIMEOUT_MS = 10_000

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
  const configured=import.meta.env.DINGTALK_CORP_ID?.trim()
  return fromUrl||configured||'CORPID'
}

export function isDingTalkEnvironment(){
  return !!window.dd?.requestAuthCode||dingTalkSdk.env.platform!=='notInDingTalk'
}

function boundedAuthorization(register:(resolve:(code:string)=>void,reject:(error:unknown)=>void)=>void){
  return new Promise<string>((resolve,reject)=>{
    let settled=false
    const finish=(action:()=>void)=>{if(settled)return;settled=true;window.clearTimeout(timer);action()}
    const timer=window.setTimeout(()=>finish(()=>reject(new DingTalkClientError('DINGTALK_AUTH_TIMEOUT','钉钉免登超时，请重新尝试。'))),DINGTALK_AUTH_TIMEOUT_MS)
    try{
      register(
        code=>finish(()=>code?resolve(code):reject(new DingTalkClientError('DINGTALK_CODE_EMPTY','钉钉授权信息无效'))),
        ()=>finish(()=>reject(new DingTalkClientError('DINGTALK_AUTH_FAILED','钉钉免登失败，请重试。'))),
      )
    }catch{finish(()=>reject(new DingTalkClientError('DINGTALK_AUTH_FAILED','钉钉免登失败，请重试。')))}
  })
}

async function requestFromInjectedApi(api:InjectedDingTalkApi,clientId:string,corpId:string){
  return boundedAuthorization((resolve,reject)=>api.requestAuthCode({
    clientId,corpId,
    success:result=>resolve(result.code??''),
    fail:reject,
  }))
}

export async function requestDingTalkAuthCode(){
  // 自动化验收会注入轻量 window.dd；真实钉钉客户端则由官方 SDK 建立 JSBridge。
  const injectedApi=window.dd?.requestAuthCode?window.dd:undefined
  if(!injectedApi&&dingTalkSdk.env.platform==='notInDingTalk'){
    throw new DingTalkClientError('NOT_IN_DINGTALK','请在钉钉内打开后使用免登。')
  }

  const clientId=import.meta.env.DINGTALK_CLIENT_ID?.trim()
  const configuredCorpId=import.meta.env.DINGTALK_CORP_ID?.trim()
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
    // requestAuthCode 无需 dd.config，授权码只能使用一次，取得后立即交给后端换取用户身份。
    const code=await boundedAuthorization((resolve,reject)=>{
      void dingTalkSdk.requestAuthCode({clientId,corpId}).then(result=>resolve(result.code??''),reject)
    })
    return {authCode:code,corpId}
  }catch(error){
    if(error instanceof DingTalkClientError)throw error
    throw new DingTalkClientError('DINGTALK_AUTH_FAILED','钉钉免登失败，请重试。')
  }
}
