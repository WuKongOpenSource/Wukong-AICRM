<template>
  <div class="max-w-4xl mx-auto space-y-8">
    <section class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
      <h3 class="text-base font-bold mb-6 flex items-center gap-2">
        <span class="w-1 h-4 bg-primary rounded-full"></span>
        个人资料
      </h3>
      <div class="flex items-center justify-between pb-6 border-b border-slate-200">
        <div class="flex items-center">
          <div class="relative group cursor-pointer" @click="avatarInputRef?.click()">
            <div v-if="avatarPreviewUrl || profileForm.imgUrl" class="size-16 rounded-2xl overflow-hidden">
              <img :src="avatarPreviewUrl || profileForm.imgUrl" class="w-full h-full object-cover" alt="avatar" />
            </div>
            <div v-else class="size-16 rounded-2xl bg-primary/10 text-primary flex items-center justify-center text-2xl font-bold">
              {{ profileForm.realname?.charAt(0) || userStore.realname?.charAt(0) || 'U' }}
            </div>
            <div class="absolute inset-0 rounded-2xl bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
              <span v-if="avatarUploading" class="material-symbols-outlined text-white text-xl animate-spin">progress_activity</span>
              <span v-else class="material-symbols-outlined text-white text-xl">photo_camera</span>
            </div>
            <input ref="avatarInputRef" type="file" accept="image/*" class="hidden" @change="handleAvatarChange" />
          </div>
          <div class="ml-4">
            <div class="text-xl font-bold text-slate-900">{{ profileForm.realname || userStore.realname }}</div>
            <div class="text-sm text-slate-500">{{ profileForm.position || '员工' }}</div>
          </div>
        </div>
      </div>

      <el-form :model="profileForm" label-position="top" class="mt-6">
        <el-row :gutter="24">
          <el-col :xs="24" :sm="12">
            <el-form-item label="姓名">
              <el-input v-model="profileForm.realname" placeholder="请输入姓名" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :xs="24" :sm="12">
            <el-form-item label="电话">
              <el-input v-model="profileForm.phone" placeholder="请输入电话" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="部门">
              <el-input v-model="profileForm.department" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :xs="24" :sm="12">
            <el-form-item label="职位">
              <el-input v-model="profileForm.position" placeholder="请输入职位" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="flex gap-3 pt-4">
          <button class="px-4 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors shadow-lg shadow-primary/20 disabled:opacity-50" :disabled="savingProfile" @click="handleSaveProfile">
            {{ savingProfile ? '保存中...' : '保存更改' }}
          </button>
          <button class="px-4 py-2 border border-slate-200 rounded-lg text-sm text-slate-600 hover:bg-slate-50 transition-colors" @click="resetProfileForm">取消</button>
        </div>
      </el-form>
    </section>

    <section class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
      <h3 class="text-base font-bold mb-6 flex items-center gap-2">
        <span class="w-1 h-4 bg-primary rounded-full"></span>
        密码修改
      </h3>
      <el-form :model="passwordForm" label-position="top" class="max-w-md">
        <el-form-item label="当前密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
        <button class="px-4 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors shadow-lg shadow-primary/20 disabled:opacity-50" :disabled="submitting" @click="handleChangePassword">
          {{ submitting ? '修改中...' : '修改密码' }}
        </button>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getLoginUserDetail, updateProfile, changePassword } from '@/api/auth'
import { getPresignedUploadUrl, uploadToMinIO } from '@/api/file'

const userStore = useUserStore()

const savingProfile = ref(false)
const avatarUploading = ref(false)
const avatarInputRef = ref<HTMLInputElement | null>(null)
const avatarPreviewUrl = ref('')
const submitting = ref(false)

const profileForm = reactive({
  img: '',
  imgUrl: '',
  realname: '',
  email: '',
  phone: '',
  department: '',
  position: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

onMounted(async () => {
  await loadProfile()
})

async function loadProfile() {
  try {
    const detail = await getLoginUserDetail()
    profileForm.img = detail.img || ''
    profileForm.imgUrl = (detail as any).imgUrl || ''
    profileForm.realname = detail.realname || ''
    profileForm.email = detail.email || ''
    profileForm.phone = detail.mobile || ''
    profileForm.department = detail.deptName || ''
    profileForm.position = detail.post || ''
  } catch {
    const info = userStore.userInfo as any
    profileForm.img = info?.img || ''
    profileForm.imgUrl = info?.imgUrl || ''
    profileForm.realname = userStore.realname || ''
    profileForm.email = info?.email || ''
    profileForm.phone = info?.mobile || ''
    profileForm.department = info?.deptName || ''
    profileForm.position = info?.post || ''
  }
}

async function handleAvatarChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过5MB')
    return
  }

  avatarUploading.value = true
  try {
    const presigned = await getPresignedUploadUrl(file.name, file.type)
    await uploadToMinIO(file, presigned.uploadUrl)
    profileForm.img = presigned.objectKey
    avatarPreviewUrl.value = presigned.accessUrl
    ElMessage.success('头像上传成功，点击保存更改生效')
  } catch {
    ElMessage.error('头像上传失败')
  } finally {
    avatarUploading.value = false
    if (avatarInputRef.value) {
      avatarInputRef.value.value = ''
    }
  }
}

function resetProfileForm() {
  const info = userStore.userInfo as any
  profileForm.img = info?.img || ''
  profileForm.imgUrl = info?.imgUrl || ''
  avatarPreviewUrl.value = ''
  profileForm.realname = userStore.realname || ''
  profileForm.email = info?.email || ''
  profileForm.phone = info?.mobile || ''
  profileForm.department = info?.deptName || ''
  profileForm.position = info?.post || ''
}

async function handleSaveProfile(event: MouseEvent) {
  event.preventDefault()
  savingProfile.value = true
  try {
    await updateProfile({
      userId: userStore.userId,
      img: profileForm.img,
      realname: profileForm.realname,
      mobile: profileForm.phone,
      email: profileForm.email,
      post: profileForm.position
    })
    await userStore.fetchUserInfo()
    avatarPreviewUrl.value = ''
    profileForm.imgUrl = userStore.userInfo?.imgUrl || ''
    ElMessage.success('个人资料保存成功')
  } catch {
    // Error handled by interceptor
  } finally {
    savingProfile.value = false
  }
}

async function handleChangePassword(event: MouseEvent) {
  event.preventDefault()

  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('密码至少6位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  submitting.value = true
  try {
    await changePassword(passwordForm.oldPassword, passwordForm.newPassword)
    ElMessage.success('密码修改成功')
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  } catch {
    // Error handled by interceptor
  } finally {
    submitting.value = false
  }
}
</script>
