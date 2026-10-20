<template>
  <router-view v-slot="{ Component }">
    <transition name="zk-fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<script setup>
// 应用根组件：只负责渲染路由并做淡入淡出过渡
</script>
