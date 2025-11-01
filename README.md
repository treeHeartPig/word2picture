# word2picture
## 1. 需要的配置
### 1.1 comfyUI地址配置
    在application.yml中配置comfyUI地址:comfyui.api.base-url
### 1.1 comfyUI工作流配置
    在resoures/workflow目录下挂载comfyui的工作流json文件，并在代码中读取
    com.zlz.word2picture.word2picture.service.ComfyUIService.loadWorkflowFromResource