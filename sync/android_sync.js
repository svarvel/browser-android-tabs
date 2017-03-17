
var callbackList = {} // message name to callback function

if (!self.chrome || !self.chrome.ipc) {
    self.chrome = {}
    const ipc = {}

    ipc.once = (message, cb) => {
        callbackList[message] = cb
        injectedObject.handleMessage(message, '0', '0')
    }

    ipc.on = ipc.once

    ipc.send = (message, arg1, arg2) => {
        var arg2ToPass = arg2
        if (undefined != arg2 && typeof arg2 != 'string' && 'save-init-data' != message) {
            arg2ToPass = JSON.stringify(arg2);
        }
        injectedObject.handleMessage(message, undefined != arg1 ? arg1.toString() : arg1, undefined != arg2ToPass ? arg2ToPass.toString() : arg2ToPass)
    }

    self.chrome.ipc = ipc
    chrome.ipcRenderer = chrome.ipc
}
