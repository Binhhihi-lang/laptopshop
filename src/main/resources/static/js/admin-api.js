// Toàn bộ lời gọi API dùng cho khu vực Admin.
// Sau này thêm ProductAPI, OrderAPI... thì viết tiếp vào file này.
const API_BASE = '/api/v1';

/**
 * Gọi API và tự parse JSON, tự ném lỗi kèm message rõ ràng khi request thất bại.
 */
async function apiRequest(path, options = {}) {
  try {
    // 1. Gọi dữ liệu
    const response = await fetch(`${API_BASE}${path}`, options);

    if (response.status === 204) return null;

    // 2. Kiểm tra kiểu dữ liệu trả về
    const contentType = response.headers.get('content-type') || '';
    const data = contentType.includes('application/json') ? await response.json() : null; 

    // 3. Nếu Server trả về mã lỗi (4xx, 5xx)
    if (!response.ok) {
      console.group("[API ERROR DETECTED]");
      console.error(`Đường dẫn lỗi: ${API_BASE}${path}`);
      console.error(`Mã trạng thái (Status): ${response.status}`);
      console.error("Dữ liệu lỗi chi tiết từ Server trả về:", data);
      console.groupEnd();

      const message = (data && (data.message || data.error)) || `Đã có lỗi xảy ra (mã ${response.status})`;
      throw new Error(message); // Ném lỗi ra ngoài để file HTML bắt được và hiện showToast
    }
    
    return data;

  } catch (networkError) {
    // LỖI ĐỘC LẬP (Mất mạng, hoặc bị Tomcat ngắt kết nối ngang xương)
    console.group(" [CRITICAL NETWORK/SERVER ERROR]");
    console.error("Chi tiết lỗi hệ thống:", networkError);
    console.warn("Đoạn này thường kích hoạt khi file quá to, Tomcat bóp nghẹt băng thông và ép hủy Request!");
    console.groupEnd();

    // Vẫn phải throw tiếp để nút "Lưu sản phẩm" ở Frontend không bị kẹt chữ "Đang lưu..."
    throw new Error(networkError.message || "Không thể kết nối đến máy chủ.");
  }
}

const UserAPI = {
  getAll: () => apiRequest('/users'),
  getById: (id) => apiRequest(`/users/${id}`),
  create: (formData) => apiRequest('/users', { method: 'POST', body: formData }),
  update: (id, formData) => apiRequest(`/users/${id}`, { method: 'PUT', body: formData }),
  remove: (id) => apiRequest(`/users/${id}`, { method: 'DELETE' }),
};

const RoleAPI = {
  getAll: () => apiRequest('/roles'),
};

const CategoryAPI = {
  getAll: () => apiRequest('/categories'),
  getById: (id) => apiRequest(`/categories/${id}`),
  create: (formData) => apiRequest('/categories', { method: 'POST', body: formData }),
  update: (id, formData) => apiRequest(`/categories/${id}`, { method: 'PUT', body: formData }),
  remove: (id) => apiRequest(`/categories/${id}`, { method: 'DELETE' }),
};

const ProductAPI = {
  getAll: () => apiRequest('/products'),
  getById: (id) => apiRequest(`/products/${id}`),
  create: (formData) => apiRequest('/products', { method: 'POST', body: formData }),
  update: (id, formData) => apiRequest(`/products/${id}`, { method: 'PUT', body: formData }),
  remove: (id) => apiRequest(`/products/${id}`, { method: 'DELETE' }),
};


const CouponAPI = {
  getAll: () => apiRequest('/coupons'),
  getById: (id) => apiRequest(`/coupons/${id}`),
  create: (payload) => apiRequest('/coupons', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }),
  update: (id, payload) => apiRequest(`/coupons/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }),
  remove: (id) => apiRequest(`/coupons/${id}`, { method: 'DELETE' }),
};

// Định dạng yyyy-MM-dd -> dd/MM/yyyy, dùng chung cho coupon
function formatDate(isoDate) {
  if (!isoDate) return 'Không giới hạn';
  const [y, m, d] = isoDate.split('-');
  return `${d}/${m}/${y}`;
}

// Hiển thị hình thức giảm giá của coupon: theo % hoặc theo số tiền cố định.
// Coupon chỉ có đúng 1 trong 2 trường discountPercent/discountAmount khác null.
function formatDiscount(coupon) {
  if (coupon.discountAmount != null) return formatCurrency(coupon.discountAmount);
  if (coupon.discountPercent != null) return coupon.discountPercent + '%';
  return '—';
}

// Các hàm tiện ích (helper functions) để hiển thị hình ảnh người dùng, sản phẩm, danh mục
// Khớp với WebMvcConfig: /images-upload/** -> file:///{upload.directory}
function avatarUrl(user) {
  if (user.avatar) return `/images-upload/avatar/${user.avatar}`;
  return `https://ui-avatars.com/api/?background=17B890&color=fff&name=${encodeURIComponent(user.fullName || user.email)}`;
}

function productImageUrl(product) {
  if (product.image) return `/images-upload/product/${product.image}`;
  return `https://ui-avatars.com/api/?background=6B7280&color=fff&name=${encodeURIComponent(product.name || 'SP')}`;
}

function categoryImageUrl(category) {
  if (category.image) return `/images-upload/category/${category.image}`;
  return `https://ui-avatars.com/api/?background=2F5FD6&color=fff&name=${encodeURIComponent(category.name || 'DM')}`;
}

// Định dạng số thành tiền VNĐ, vd 15000000 -> "15.000.000 đ"
function formatCurrency(value) {
  return new Intl.NumberFormat('vi-VN').format(value || 0) + ' đ';
}