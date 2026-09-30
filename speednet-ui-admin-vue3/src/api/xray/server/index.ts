import request from '@/config/axios'
export interface XrayServerVO {id?:number;name:string;host:string;sshPort:number;sshUsername:string;sshAuthType:number;sshPassword?:string;sshPrivateKey?:string;sshKeyPassphrase?:string;sshCredentialConfigured?:boolean;panelScheme?:string;panelPort?:number;panelPath?:string;panelToken?:string;panelTokenConfigured?:boolean;panelVersion?:string;xrayVersion?:string;installStatus?:number;healthStatus?:number;lastCheckTime?:Date;lastError?:string;remark?:string;createTime?:Date}
export const getServerPage=(params:any)=>request.get({url:'/xray/server/page',params})
export const getServer=(id:number)=>request.get({url:'/xray/server/get?id='+id})
export const createServer=(data:XrayServerVO)=>request.post({url:'/xray/server/create',data})
export const updateServer=(data:XrayServerVO)=>request.put({url:'/xray/server/update',data})
export const deleteServer=(id:number)=>request.delete({url:'/xray/server/delete?id='+id})
export const testSsh=(id:number)=>request.post({url:'/xray/server/test-ssh?id='+id})
export const checkHealth=(id:number)=>request.post({url:'/xray/server/check-health?id='+id})
