import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './styles/index.css'
import './styles/list.css'
import './styles/motion.css'
import { installMotion } from './composables/motion'

const app = createApp(App)

app.use(router)
app.use(ElementPlus, { locale: zhCn })
// 全局动效指令：v-reveal（元素进视口时依次浮现，只播一次）
app.use(installMotion)

app.mount('#app')
