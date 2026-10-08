# ============================================================
# Backend LaptopShop — Dockerfile multi-stage
# Stage 1 dùng Maven + JDK để build ra file .jar
# Stage 2 chỉ giữ JRE + .jar nên image nhỏ hơn nhiều
# ============================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy pom.xml riêng một lớp để tận dụng cache của Docker:
# khi chỉ sửa code Java (không đổi dependency), Docker bỏ qua bước tải thư viện.
COPY pom.xml .
RUN mvn -B -q dependency:resolve

COPY src ./src

# Bỏ qua test khi đóng gói: test cần môi trường riêng và làm build chậm.
# (JaCoCo gate 60% nằm ở phase `verify`, mà `package` dừng trước đó nên không chặn.)
RUN mvn -B clean package -DskipTests

# ---------- Stage 2:  Lấy image eclipse chứa java để chạy  ----------
FROM eclipse-temurin:17-jre

# Múi giờ Việt Nam: LocalDateTime.now() dùng cho vnp_CreateDate (VNPay) và
# mốc hạn thanh toán. Để UTC sẽ lệch 7 giờ so với giờ VNPay mong đợi.
ENV TZ=Asia/Ho_Chi_Minh

WORKDIR /app
COPY --from=build /build/target/laptopshop-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
# tạo câu lệnh chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]
