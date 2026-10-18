<template>
  <div class="work-form-grid">
    <el-form-item
      v-for="field in fields"
      :key="field.key"
      :label="field.label"
      :required="field.required"
      :error="errors[field.key]"
      :class="{ 'work-wide': field.kind === 'textarea' }"
    >
      <el-radio-group
        v-if="field.kind === 'radio'"
        v-model="model[field.key]"
        :aria-label="field.label"
      >
        <el-radio v-for="option in field.options" :key="option" :value="option">
          {{ option }}
        </el-radio>
      </el-radio-group>
      <el-select
        v-else-if="field.options"
        v-model="model[field.key]"
        :aria-label="field.label"
        clearable
        filterable
        allow-create
        default-first-option
      >
        <el-option
          v-for="option in field.options"
          :key="option"
          :label="option"
          :value="option"
        />
      </el-select>
      <el-date-picker
        v-else-if="field.kind === 'date' || field.kind === 'month'"
        v-model="model[field.key]"
        :aria-label="field.label"
        :type="field.kind"
        :value-format="field.kind === 'month' ? 'YYYY-MM' : 'YYYY-MM-DD'"
      />
      <el-input-number
        v-else-if="field.kind === 'number'"
        v-model="model[field.key]"
        :aria-label="field.label"
        :min="1"
        :precision="0"
      />
      <el-input
        v-else
        v-model="model[field.key]"
        :aria-label="field.label"
        :type="field.kind === 'textarea' ? 'textarea' : 'text'"
        :rows="3"
        :placeholder="field.hint || `请输入${field.label}`"
        :maxlength="field.kind === 'textarea' ? 5000 : 500"
      />
    </el-form-item>
  </div>
</template>
<script setup>
defineProps({
  fields: Array,
  model: Object,
  errors: { type: Object, default: () => ({}) },
})
</script>
