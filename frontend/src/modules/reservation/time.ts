export const ZONE='Asia/Shanghai'
export function localTime(value:string|number){const date=new Date(value);if(!Number.isFinite(date.getTime()))return'';const p=new Intl.DateTimeFormat('en-CA',{timeZone:ZONE,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).formatToParts(date);const field=(name:string)=>p.find(part=>part.type===name)?.value??'';return `${field('year')}-${field('month')}-${field('day')}T${field('hour')}:${field('minute')}:${field('second')}`}
export const displayTime=(value:string|number|null|undefined)=>value===null||value===undefined?'':localTime(value).replace('T',' ')
export const inputTime=(value:string|null)=>value?localTime(value).slice(0,16):''
export function instant(value:string){if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/.test(value))return null;const result=value+(value.length===16?':00':'')+'+08:00';return Number.isFinite(Date.parse(result))?result:null}
export function hourly(start:string,end:string){const a=new Date(start),b=new Date(end);return b.getTime()>a.getTime()&&a.getUTCMinutes()===0&&b.getUTCMinutes()===0&&a.getUTCSeconds()===0&&b.getUTCSeconds()===0&&a.getUTCMilliseconds()===0&&b.getUTCMilliseconds()===0}
export function queryInstant(value:unknown){return typeof value==='string'&&/(?:Z|[+-]\d{2}:\d{2})$/i.test(value)&&Number.isFinite(Date.parse(value))?value:null}
