<template><div /></template>
<script setup lang="ts">
import { useRouter } from 'vue-router'
defineOptions({ name: 'ProductSpu' })
const router = useRouter()
onMounted(() => router.replace('/subscription/plans'))
</script>
