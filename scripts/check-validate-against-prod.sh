#!/usr/bin/env bash
# Doi chieu entity (Java) voi cau truc bang THAT tren DB — CHI DOC, KHONG GHI.
#
# Tai sao can file nay: tu khi chuyen sang Flyway, application.properties dung
# spring.jpa.hibernate.ddl-auto=validate. Che do nay khong SUA bang nao ca, no
# doi chieu entity voi bang that va TU CHOI khoi dong neu lech. Lech o dau thi
# bao loi o do — tot, nhung phai biet cach doc no.
#
# Flyway duoc TAT trong script nay de chi con lai mot minh Hibernate len tieng,
# doc ket qua cho de dang.
#
# App van giu port va chay cho toi khi bi timeout giết, nen ma thoat 124 là BÌNH
# THUONG (nghia la app da len duoc). Ket luan duoc rut tu log, khong tu ma thoat.
#
# Chay:  ./scripts/check-validate-against-prod.sh
set -uo pipefail
cd "$(dirname "$0")/.."   # ve thu muc project

: "${LAPTOPSHOP_DB_URL:?Thieu LAPTOPSHOP_DB_URL (chuoi ket noi Aiven)}"
: "${DB_LAPTOPSHOP_USERNAME:?Thieu DB_LAPTOPSHOP_USERNAME}"
: "${DB_LAPTOPSHOP_PASSWORD:?Thieu DB_LAPTOPSHOP_PASSWORD}"

LOG=/tmp/validate-prod.log
echo ">> Dang doi chieu entity vs bang that (khong ghi bat cu thu gi)..."

timeout 300 ./mvnw -o -q spring-boot:run \
  -Dspring-boot.run.arguments="--spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=validate --spring.jpa.show-sql=false --server.port=0" \
  > "$LOG" 2>&1

echo "------------------------------------------------------------"
if grep -qi "Schema-validation" "$LOG"; then
  echo "LECH SCHEMA - entity va bang that khong khop:"
  grep -i "Schema-validation" "$LOG" | sed 's/^/   /' | sort -u | head -40
  echo ""
  echo "   => App se khong khoi dong duoc. Sua entity hoac viet migration cho khop."
  RC=1
elif grep -q "Started LaptopshopApplication" "$LOG"; then
  echo "PASS - moi entity khop voi bang that."
  RC=0
else
  echo "KHONG KET LUAN DUOC - app khong loi cung khong len duoc. Xem log:"
  tail -25 "$LOG" | sed 's/^/   /'
  RC=1
fi
echo "------------------------------------------------------------"
echo "Log day du: $LOG"
exit $RC
