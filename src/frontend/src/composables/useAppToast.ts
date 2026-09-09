import { onBeforeUnmount, ref } from 'vue'

export type ToastKind='info'|'success'|'warning'|'error'
export interface ToastState { message:string;kind:ToastKind }

export function useAppToast(duration=1000){
  const toast=ref<ToastState>({message:'',kind:'info'})
  let timer:ReturnType<typeof setTimeout>|undefined
  function showToast(message:string,kind:ToastKind='info'){
    if(timer)clearTimeout(timer)
    toast.value={message,kind}
    timer=setTimeout(()=>{toast.value={message:'',kind}},duration)
  }
  function showApiError(error:unknown,fallback:string){
    const candidate=error as {message?:string;status?:number;code?:string}
    const message=candidate?.message||fallback
    const business=typeof candidate?.status==='number'&&candidate.status>=400&&candidate.status<500&&candidate.status!==401&&candidate.status!==403
    showToast(message,business?'warning':'error')
  }
  onBeforeUnmount(()=>{if(timer)clearTimeout(timer)})
  return {toast,showToast,showApiError}
}
