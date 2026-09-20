<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props=defineProps<{modelValue:string;label:string}>()
const emit=defineEmits<{ 'update:modelValue':[value:string] }>()
const root=ref<HTMLElement|null>(null),open=ref(false),view=ref(new Date()),draft=ref('')
const weekdays=['一','二','三','四','五','六','日']
function parse(value:string){const [y,m,d]=value.split('-').map(Number);return new Date(y,m-1,d)}
function iso(date:Date){return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`}
function today(){return new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai'}).format(new Date())}
const title=computed(()=>`${view.value.getFullYear()}年 ${view.value.getMonth()+1}月`)
const displayValue=computed(()=>props.modelValue?`${props.modelValue.slice(5,7)}月${props.modelValue.slice(8,10)}日`:'请选择日期')
const days=computed(()=>{const y=view.value.getFullYear(),m=view.value.getMonth(),first=new Date(y,m,1),offset=(first.getDay()+6)%7,start=new Date(y,m,1-offset);return Array.from({length:42},(_,i)=>{const date=new Date(start);date.setDate(start.getDate()+i);return {value:iso(date),day:date.getDate(),outside:date.getMonth()!==m}})})
function show(){draft.value=props.modelValue;view.value=props.modelValue?parse(props.modelValue):new Date();open.value=true;nextTick(()=>root.value?.querySelector<HTMLElement>('.calendar-day.selected')?.focus())}
function move(months:number){view.value=new Date(view.value.getFullYear(),view.value.getMonth()+months,1)}
function select(value:string){draft.value=value}
function confirm(){if(draft.value)emit('update:modelValue',draft.value);open.value=false}
function outside(event:PointerEvent){if(open.value&&!root.value?.contains(event.target as Node))open.value=false}
function key(event:KeyboardEvent){if(event.key==='Escape')open.value=false}
watch(open,value=>{if(value){document.addEventListener('pointerdown',outside);document.addEventListener('keydown',key)}else{document.removeEventListener('pointerdown',outside);document.removeEventListener('keydown',key)}})
onBeforeUnmount(()=>{document.removeEventListener('pointerdown',outside);document.removeEventListener('keydown',key)})
</script>

<template>
  <div ref="root" class="date-picker">
    <button type="button" class="date-picker-trigger" :aria-label="label" :aria-expanded="open" @click.stop="open ? open=false : show()"><span>{{ displayValue }}</span><CalendarDays :size="18"/></button>
    <div v-if="open" class="calendar-popover" role="dialog" :aria-label="`${label}日历`" @pointerdown.stop @click.stop>
      <header><button type="button" aria-label="上个月" @click="move(-1)"><ChevronLeft/></button><strong>{{ title }}</strong><button type="button" aria-label="下个月" @click="move(1)"><ChevronRight/></button></header>
      <div class="calendar-weekdays"><span v-for="day in weekdays" :key="day">{{ day }}</span></div>
      <div class="calendar-grid"><button v-for="item in days" :key="item.value" type="button" class="calendar-day" :class="{outside:item.outside,today:item.value===today(),selected:item.value===draft}" :aria-label="item.value" :aria-selected="item.value===draft" @click="select(item.value)">{{ item.day }}</button></div>
      <footer class="calendar-actions"><button type="button" @pointerdown.stop @click.stop="open=false">取消</button><button type="button" class="confirm" :disabled="!draft" @pointerdown.stop @click.stop="confirm">确定</button></footer>
    </div>
  </div>
</template>
