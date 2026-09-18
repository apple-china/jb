<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'

interface Slot { time:string; available:boolean; reason?:string }
interface WheelItem { key:string;value:string;reason?:string }
const props=defineProps<{modelValue:string;slots:Slot[]}>()
const emit=defineEmits<{ 'update:modelValue':[value:string] }>()
const hourWheel=ref<HTMLElement|null>(null),minuteWheel=ref<HTMLElement|null>(null)
const selectedHour=ref(''),selectedMinute=ref('')
const availableSlots=computed(()=>props.slots.filter(slot=>slot.available).slice().sort((a,b)=>a.time.localeCompare(b.time)))
const hours=computed(()=>[...new Set(props.slots.filter(slot=>slot.available).map(slot=>slot.time.slice(0,2)))].sort((a,b)=>Number(a)-Number(b)))
const slotMap=computed(()=>new Map(props.slots.map(slot=>[slot.time,slot])))
function hourAvailable(hour:string){return props.slots.some(slot=>slot.time.startsWith(`${hour}:`)&&slot.available)}
const hourItems=computed<WheelItem[]>(()=>hours.value.map(hour=>({key:hour,value:hour})))
const minuteItems=computed<WheelItem[]>(()=>props.slots.filter(slot=>slot.available&&slot.time.startsWith(`${selectedHour.value}:`)).map(slot=>({key:slot.time,value:slot.time.slice(3,5),reason:slot.reason})).sort((a,b)=>Number(a.value)-Number(b.value)))
const normalizing=new Set<'hour'|'minute'>(),timers=new Map<string,ReturnType<typeof setTimeout>>()
function center(wheel:HTMLElement|null,value:string){nextTick(()=>{const target=wheel?.querySelector<HTMLElement>(`[data-value="${value}"]`);if(!wheel||!target)return;const kind=wheel===hourWheel.value?'hour':'minute';normalizing.add(kind);wheel.scrollTo({top:target.offsetTop-(wheel.clientHeight-target.offsetHeight)/2,behavior:'auto'});setTimeout(()=>normalizing.delete(kind),80)})}
function emitCurrent(){const slot=slotMap.value.get(`${selectedHour.value}:${selectedMinute.value}`);if(slot?.available)emit('update:modelValue',slot.time)}
function firstMinute(hour:string){return availableSlots.value.find(slot=>slot.time.startsWith(`${hour}:`))?.time.slice(3,5)??''}
function chooseHour(hour:string){if(!hourAvailable(hour))return;selectedHour.value=hour;if(!slotMap.value.get(`${hour}:${selectedMinute.value}`)?.available)selectedMinute.value=firstMinute(hour);center(hourWheel.value,hour);center(minuteWheel.value,selectedMinute.value);emitCurrent()}
function chooseMinute(minute:string){const slot=slotMap.value.get(`${selectedHour.value}:${minute}`);if(!slot?.available)return;selectedMinute.value=minute;center(minuteWheel.value,minute);emitCurrent()}
function settle(kind:'hour'|'minute'){if(normalizing.has(kind))return;const wheel=kind==='hour'?hourWheel.value:minuteWheel.value;if(!wheel)return;clearTimeout(timers.get(kind));timers.set(kind,setTimeout(()=>{if(normalizing.has(kind))return;const centerLine=wheel.scrollTop+wheel.clientHeight/2,buttons=[...wheel.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')];const closest=buttons.sort((a,b)=>Math.abs(a.offsetTop+a.offsetHeight/2-centerLine)-Math.abs(b.offsetTop+b.offsetHeight/2-centerLine))[0];if(!closest)return;kind==='hour'?chooseHour(closest.dataset.value??''):chooseMinute(closest.dataset.value??'')},120))}
function sync(value:string){if(!value)return;selectedHour.value=value.slice(0,2);selectedMinute.value=value.slice(3,5);center(hourWheel.value,selectedHour.value);center(minuteWheel.value,selectedMinute.value)}
function initialize(){if(props.modelValue){sync(props.modelValue);return}const first=availableSlots.value[0];if(!first)return;selectedHour.value=first.time.slice(0,2);selectedMinute.value=first.time.slice(3,5);center(hourWheel.value,selectedHour.value);center(minuteWheel.value,selectedMinute.value);emitCurrent()}
watch(()=>props.modelValue,value=>sync(value))
watch(()=>props.slots,initialize,{deep:true})
onMounted(initialize)
</script>

<template>
  <div class="time-wheel-shell time-wheel-pair">
    <div class="time-wheel-focus" aria-hidden="true"/><span class="time-wheel-colon" aria-hidden="true">:</span>
    <div ref="hourWheel" class="time-wheel" role="listbox" aria-label="小时" @scroll.passive="settle('hour')">
      <button v-for="item in hourItems" :key="item.key" type="button" role="option" :data-value="item.value" :aria-selected="item.value===selectedHour" :class="{selected:item.value===selectedHour}" @click="chooseHour(item.value)">{{ item.value }}</button>
    </div>
  <div ref="minuteWheel" class="time-wheel" role="listbox" aria-label="分钟" @scroll.passive="settle('minute')">
      <button v-for="item in minuteItems" :key="item.key" type="button" role="option" :data-value="item.value" :aria-selected="item.value===selectedMinute" :title="item.reason" :class="{selected:item.value===selectedMinute}" @click="chooseMinute(item.value)">{{ item.value }}</button>
    </div>
  </div>
</template>
