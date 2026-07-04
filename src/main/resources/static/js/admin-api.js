// Toàn bộ lời gọi API dùng cho khu vực Admin.
// Sau này thêm ProductAPI, OrderAPI... thì viết tiếp vào file này.
const API_BASE = '/api/v1';

/**
 * Gọi API và tự parse JSON, tự ném lỗi kèm message rõ ràng khi request thất bại.
 */
async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, options);

  if (response.status === 204) return null; // No Content (vd sau khi xóa)

  const contentType = response.headers.get('content-type') || '';
  const data = contentType.includes('application/json') ? await response.json() : null;

  if (!response.ok) {
    const message = (data && (data.message || data.error)) || `Đã có lỗi xảy ra (mã ${response.status})`;
    throw new Error(message);
  }
  return data;
}

// khai báo các API dùng cho Admin
const UserAPI = {
  getAll: () => apiRequest('/users'),
  getById: (id) => apiRequest(`/users/${id}`),
  create: (formData) => apiRequest('/users', { method: 'POST', body: formData }),
  update: (id, formData) => apiRequest(`/users/${id}`, { method: 'PUT', body: formData }),
  remove: (id) => apiRequest(`/users/${id}`, { method: 'DELETE' }),
};

// Api cho vai trò (Role) dùng trong Admin đổ danh sách vai trò lên select khi tạo/sửa người dùng.
const RoleAPI = {
  getAll: () => apiRequest('/roles'),
};

// Trả về URL ảnh đại diện của người dùng. Nếu chưa có avatar thì trả về URL ảnh mặc định từ ui-avatars.com
// Khớp với WebMvcConfig: /images-upload/** -> file:///{upload.directory}
function avatarUrl(user) {
  if (user.avatar) return `/images-upload/avatar/${user.avatar}`;
  return `https://ui-avatars.com/api/?background=17B890&color=fff&name=${encodeURIComponent(user.fullName || user.email)}`;
}

// Dùng cho module Product sắp tới.
function productImageUrl(product) {
  if (product.image) return `/images-upload/product/${product.image}`;
  return `https://ui-avatars.com/api/?background=6B7280&color=fff&name=${encodeURIComponent(product.name || 'SP')}`;
}