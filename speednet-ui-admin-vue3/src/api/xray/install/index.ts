import request from '@/config/axios'
export interface InstallTaskVO {id:number;serverId:number;version?:string;status:number;currentStep:string;startTime?:Date;endTime?:Date;errorMessage?:string;createTime:Date}
export interface InstallLogVO {id:number;taskId:number;level:number;step:string;content:string;createTime:Date}
export const createInstall=(data:{serverId:number;version?:string})=>request.post({url:'/xray/install/create',data})
export const getInstallPage=(params:any)=>request.get({url:'/xray/install/page',params})
export const getInstallLogs=(taskId:number)=>request.get({url:'/xray/install/logs?taskId='+taskId})
