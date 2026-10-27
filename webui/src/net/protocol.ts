// Normative message protocol between browser and gateway (see the plan's protocol section).

/** Browser to gateway. */
export type In =
 | {t:"hello", nick:string, turnstile?:string, idToken?:string, dev?:{sub:string, groups:string[]}}
 | {t:"command", script:string}
 | {t:"takeControl"} | {t:"release"}
 | {t:"chat", text:string}
 | {t:"selectCamera", camera:string}
 | {t:"cameraSettings", camera:string, on:boolean, width:number, height:number, zoom:number, quality:number, fixed:boolean}
 | {t:"newImage", camera:string}
 | {t:"workspaceAdd", x:number, y:number, z:number, label:string}
 | {t:"workspaceDelete", id:string}
 | {t:"archiveImages"}
 | {t:"blocks", action:"add"|"randomise"|"reset"}
 | {t:"activity"}
 | {t:"admin", action:"kick"|"ban"|"reset"|"deleteMove"|"deleteSnapshot"|"deleteChat", nick?:string, id?:string}

/** Gateway to browser. */
export type Out =
 | {t:"welcome", nick:string, level:"guest"|"registered"|"admin", cameras:string[], commit:string,
    limits:{script:number, waypoints:number, chat:number}}
 | {t:"robotState", x:number, y:number, z:number, spin:number, tilt:number, gripperOpen:boolean, moving:boolean}
 | {t:"controller", nick:string|null}
 | {t:"commandStatus", id:string, state:"queued"|"sent"|"done"|"error", script:string, text:string}
 | {t:"users", nicks:string[]}
 | {t:"chat", id:string, nick:string, text:string, time:number}
 | {t:"workspace", points:{id:string, x:number, y:number, z:number, label:string, owner:string}[]}
 | {t:"image", camera:string, seq:number, width:number, height:number, calibration:number[]}
 | {t:"cameraSettings", camera:string, on:boolean, width:number, height:number, zoom:number, quality:number, fixed:boolean}
 | {t:"archived", kind:"move"|"snapshot", id:string, url:string}
 | {t:"lab", state:"ready"|"stopping", message:string}
 | {t:"console", text:string}
 | {t:"error", code:"bad-message"|"rate-limited"|"not-in-control"|"unauthorised"|"lab-unavailable"|"too-many", message:string}
