<template>
  <section class="work-page">
    <header class="work-page-heading">
      <div>
        <p class="work-eyebrow">研究所工作台</p>
        <h1>{{ title }}</h1>
        <p v-if="description" class="work-muted">{{ description }}</p>
      </div>
      <div class="work-actions"><slot name="actions" /></div>
    </header>
    <slot />
  </section>
</template>
<script setup>
defineProps({ title: String, description: String })
</script>
