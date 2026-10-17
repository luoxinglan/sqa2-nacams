<script setup>
defineProps({ title: { type: String, required: true }, subtitle: { type: String, default: '' } })
</script>

<template>
  <section class="container page-shell">
    <div class="page-heading">
      <p class="eyebrow">SQA2 · NACAMS</p>
      <h1>{{ title }}</h1>
      <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
    </div>
    <slot />
  </section>
</template>
