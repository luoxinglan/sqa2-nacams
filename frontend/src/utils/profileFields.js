// 与 UserService.PROFILE/SELF 字段白名单一致，不向本人保存接口发送身份和权限字段。
export const SELF_FIELDS = [
  { key: 'englishFamilyName', label: '英文姓' },
  { key: 'englishGivenName', label: '英文名' },
  { key: 'birthDate', label: '出生日期', kind: 'date' },
  { key: 'email', label: '电子邮箱' },
  { key: 'countryCode', label: '国家代码', hint: '例如 +86' },
  { key: 'mobile', label: '移动电话', hint: '11 位数字' },
  {
    key: 'officePhone',
    label: '办公电话',
    hint: '3 位区号及 8 位号码，如 01068910001',
  },
  { key: 'remark', label: '中文备注', kind: 'textarea' },
  { key: 'remarkEnglish', label: '英文备注', kind: 'textarea' },
]
export const COMMON_FIELDS = [
  { key: 'name', label: '中文姓名', required: true },
  { key: 'gender', label: '性别', kind: 'radio', options: ['男', '女'] },
  {
    key: 'nationality',
    label: '国籍',
    options: [
      '中国',
      '美国',
      '英国',
      '法国',
      '德国',
      '日本',
      '韩国',
      '俄罗斯',
      '其他',
    ],
  },
  ...SELF_FIELDS,
]
export const TEACHER_FIELDS = [
  { key: 'unit', label: '工作单位' },
  { key: 'unitEnglish', label: '工作单位英文名' },
  { key: 'title', label: '职称' },
  { key: 'supervisorQualification', label: '导师资格' },
  { key: 'position', label: '研究所职务' },
  { key: 'socialPositions', label: '社会兼职' },
  { key: 'collegePosition', label: '学院行政职务' },
  { key: 'collegeAcademicPosition', label: '学院学术职务' },
]
export const STUDENT_FIELDS = [
  {
    key: 'studentNo',
    label: '学号',
    hint: '10 位数字，层次＋类别＋入学年份＋顺序号',
    required: true,
  },
  {
    key: 'status',
    label: '学籍状态',
    options: ['在读', '毕业', '结业肄业'],
    required: true,
  },
  {
    key: 'category',
    label: '培养类别',
    options: [
      '本科',
      '学术硕士',
      '专业硕士',
      '学术博士',
      '专业博士',
      '交换生',
      '留学生',
    ],
  },
  { key: 'enrollmentYear', label: '入学年份', kind: 'number' },
  { key: 'enrollmentDate', label: '入学时间', kind: 'month' },
  { key: 'advisor', label: '导师姓名' },
  { key: 'advisorUnit', label: '导师单位' },
  {
    key: 'advisorEmployeeNo',
    label: '导师工号',
    hint: '可为本所之外的学校全职教师',
  },
  { key: 'advisorUserId', label: '导师用户 ID', kind: 'number' },
  { key: 'coAdvisor', label: '副导师' },
  { key: 'graduationDate', label: '毕业时间', kind: 'month' },
  { key: 'firstEmployer', label: '初次就业单位' },
]
export function userPayload(form, keys) {
  const out = {}
  for (const key of keys) {
    if (form[key] !== undefined) out[key] = form[key]
  }
  for (const key of ['enrollmentYear', 'advisorUserId']) {
    if (out[key] === '' || out[key] == null) delete out[key]
  }
  return out
}
