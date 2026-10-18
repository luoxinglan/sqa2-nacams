<template>
  <LoadError :message="error" @retry="$emit('load')" />
  <div class="work-table" v-loading="loading"><slot /></div>
  <el-pagination
    v-if="total > 0"
    class="work-pagination"
    :current-page="query.page"
    :page-size="query.page_size"
    :total="total"
    layout="total, prev, pager, next"
    @update:current-page="change"
  />
</template>
<script setup>
import LoadError from './LoadError.vue'
const props = defineProps({
  query: Object,
  total: Number,
  loading: Boolean,
  error: String,
})
const emit = defineEmits(['load'])
function change(page) {
  props.query.page = page
  emit('load')
}
</script>
