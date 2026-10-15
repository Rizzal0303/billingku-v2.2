package com.example.data.sync

object BillingkuPhpScript {
    val PHP_CODE = """
<?php
/**
 * =========================================================================
 * BILLINGKU WIFI NETWORK - REST API & DATABASE SYNC SERVER
 * File: billingku.php
 * Versi: 1.0 (Production Ready)
 * 
 * Panduan Pemasangan:
 * 1. Unggah (upload) file ini ke hosting cPanel (folder public_html) atau VPS/XAMPP Anda.
 * 2. Buat database MySQL baru, misalnya: billingku_db
 * 3. Ubah konfigurasi database di bawah ini (${'$'}db_host, ${'$'}db_user, ${'$'}db_pass, ${'$'}db_name, ${'$'}api_key).
 * 4. Buka URL ini di browser untuk verifikasi awal.
 * 5. Buka Menu Pengaturan -> Database MySQL di Aplikasi Android Billingku, lalu
 *    masukkan URL file ini dan API Key Anda.
 * =========================================================================
 */

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-API-Key');

if (${'$'}_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

// -------------------------------------------------------------
// 1. KONFIGURASI DATABASE MYSQL & KEAMANAN
// Mendukung file config.php terpusat jika tersedia, atau konfigurasi manual di bawah ini
// -------------------------------------------------------------
${'$'}db_host = 'localhost';
${'$'}db_name = 'billingku_db';     // Nama database MySQL Anda
${'$'}db_user = 'root';              // Username database MySQL Anda
${'$'}db_pass = '';                  // Password database MySQL Anda
${'$'}api_key = 'billingku_secret';  // Samakan dengan API Key di Pengaturan Aplikasi

if (file_exists(__DIR__ . '/config.php')) {
    require_once __DIR__ . '/config.php';
    if (function_exists('getDbConnection')) {
        ${'$'}pdo = getDbConnection();
    }
    if (defined('API_KEY')) {
        ${'$'}api_key = API_KEY;
    }
}

// -------------------------------------------------------------
// 2. KONEKSI KE DATABASE (PDO) JIKA BELUM TERINISIALISASI
// -------------------------------------------------------------
if (!isset(${'$'}pdo) || !(${'$'}pdo instanceof PDO)) {
    try {
        ${'$'}pdo = new PDO("mysql:host={${'$'}db_host};dbname={${'$'}db_name};charset=utf8mb4", ${'$'}db_user, ${'$'}db_pass, [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
    } catch (PDOException ${'$'}e) {
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal terkoneksi ke database MySQL: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// -------------------------------------------------------------
// 3. FUNGSI OTOMATIS MEMBUAT TABEL (AUTO MIGRATION)
// -------------------------------------------------------------
function initTables(${'$'}pdo) {
    // Tabel Pelanggan
    ${'$'}pdo->exec("CREATE TABLE IF NOT EXISTS `customers` (
        `id` BIGINT NOT NULL PRIMARY KEY,
        `customerCode` VARCHAR(50) NOT NULL,
        `name` VARCHAR(150) NOT NULL,
        `phone` VARCHAR(50) DEFAULT '',
        `address` TEXT,
        `odpLocation` VARCHAR(150) DEFAULT '',
        `packageId` BIGINT DEFAULT 0,
        `packageName` VARCHAR(100) DEFAULT '',
        `monthlyFee` BIGINT DEFAULT 0,
        `dueDay` INT DEFAULT 10,
        `ipOrPppoe` VARCHAR(100) DEFAULT '',
        `status` VARCHAR(30) DEFAULT 'ACTIVE',
        `createdAt` BIGINT DEFAULT 0,
        `updatedAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX (`customerCode`),
        INDEX (`status`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    // Tabel Paket Internet
    ${'$'}pdo->exec("CREATE TABLE IF NOT EXISTS `package_plans` (
        `id` BIGINT NOT NULL PRIMARY KEY,
        `name` VARCHAR(100) NOT NULL,
        `speedMbps` INT DEFAULT 0,
        `price` BIGINT DEFAULT 0,
        `description` TEXT,
        `isActive` TINYINT(1) DEFAULT 1,
        `updatedAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    // Tabel Transaksi / Pembayaran
    ${'$'}pdo->exec("CREATE TABLE IF NOT EXISTS `transactions` (
        `id` BIGINT NOT NULL PRIMARY KEY,
        `invoiceNumber` VARCHAR(60) NOT NULL,
        `customerId` BIGINT NOT NULL,
        `customerName` VARCHAR(150) NOT NULL,
        `customerCode` VARCHAR(50) NOT NULL,
        `packageName` VARCHAR(100) DEFAULT '',
        `monthlyFee` BIGINT DEFAULT 0,
        `adminFee` BIGINT DEFAULT 0,
        `discount` BIGINT DEFAULT 0,
        `totalAmount` BIGINT NOT NULL,
        `periodMonth` VARCHAR(60) NOT NULL,
        `paymentMethod` VARCHAR(30) DEFAULT 'TUNAI',
        `cashierName` VARCHAR(100) DEFAULT 'Kasir Utama',
        `cashierRole` VARCHAR(50) DEFAULT 'KASIR',
        `notes` TEXT,
        `paidAt` BIGINT NOT NULL,
        `isPrinted` TINYINT(1) DEFAULT 0,
        `isDeposited` TINYINT(1) DEFAULT 0,
        `settlementBatchId` BIGINT NULL,
        `settlementBatchNumber` VARCHAR(60) NULL,
        `depositedAt` BIGINT NULL,
        `createdAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX (`customerId`),
        INDEX (`invoiceNumber`),
        INDEX (`periodMonth`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    // Pastikan constraint UNIQUE (customerId, periodMonth) ada di MySQL agar race-condition dua HP di waktu bersamaan dicegah oleh database engine
    try {
        ${'$'}pdo->exec("ALTER TABLE `transactions` ADD UNIQUE INDEX `uniq_customer_period` (`customerId`, `periodMonth`);");
    } catch (Exception ${'$'}e) {
        // Lewati jika index sudah ada
    }

    // Tabel Setoran Kasir (Settlement Batches)
    ${'$'}pdo->exec("CREATE TABLE IF NOT EXISTS `settlement_batches` (
        `id` BIGINT NOT NULL PRIMARY KEY,
        `batchNumber` VARCHAR(60) NOT NULL,
        `periodMonth` VARCHAR(60) NOT NULL,
        `cashierName` VARCHAR(100) NOT NULL,
        `recipientName` VARCHAR(100) DEFAULT 'Owner / Admin',
        `totalAmount` BIGINT NOT NULL,
        `totalCashAmount` BIGINT DEFAULT 0,
        `totalNonCashAmount` BIGINT DEFAULT 0,
        `customerCount` INT DEFAULT 0,
        `notes` TEXT,
        `createdAt` BIGINT NOT NULL,
        INDEX (`batchNumber`),
        INDEX (`periodMonth`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    // Tabel Anggota Staf & Teknisi
    ${'$'}pdo->exec("CREATE TABLE IF NOT EXISTS `staff_members` (
        `id` BIGINT NOT NULL PRIMARY KEY,
        `username` VARCHAR(60) NOT NULL,
        `name` VARCHAR(150) NOT NULL,
        `role` VARCHAR(30) DEFAULT 'KASIR',
        `phone` VARCHAR(50) DEFAULT '',
        `pin` VARCHAR(255) DEFAULT '1234',
        `isActive` TINYINT(1) DEFAULT 1,
        `updatedAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        UNIQUE KEY (`username`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    // Tabel Pengumuman & Notifikasi Gangguan (Siap dibaca oleh aplikasi pelanggan Net-pay)
    ${'$'}pdo->exec("CREATE TABLE IF NOT EXISTS `announcements` (
        `id` BIGINT NOT NULL PRIMARY KEY,
        `title` VARCHAR(200) NOT NULL,
        `category` VARCHAR(50) NOT NULL DEFAULT 'INFO',
        `message` TEXT NOT NULL,
        `targetScope` VARCHAR(100) DEFAULT 'SEMUA',
        `status` VARCHAR(30) DEFAULT 'ACTIVE',
        `estimatedEnd` VARCHAR(100) DEFAULT '',
        `createdBy` VARCHAR(100) DEFAULT 'Admin',
        `createdAt` BIGINT DEFAULT 0,
        `updatedAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX (`status`),
        INDEX (`category`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    // Pastikan kolom-kolom baru tersedia pada instalasi database lama (Safe Column Migrations)
    try { ${'$'}pdo->exec("ALTER TABLE `staff_members` MODIFY COLUMN `pin` VARCHAR(255) DEFAULT '1234'"); } catch (Exception ${'$'}e) {}
    try { ${'$'}pdo->exec("ALTER TABLE `customers` ADD COLUMN `odpLocation` VARCHAR(150) DEFAULT '' AFTER `address`"); } catch (Exception ${'$'}e) {}
    try { ${'$'}pdo->exec("ALTER TABLE `customers` ADD COLUMN `ipOrPppoe` VARCHAR(100) DEFAULT '' AFTER `dueDay`"); } catch (Exception ${'$'}e) {}
    try { ${'$'}pdo->exec("ALTER TABLE `transactions` ADD COLUMN `settlementBatchId` BIGINT NULL AFTER `isDeposited`"); } catch (Exception ${'$'}e) {}
    try { ${'$'}pdo->exec("ALTER TABLE `transactions` ADD COLUMN `settlementBatchNumber` VARCHAR(60) NULL AFTER `settlementBatchId`"); } catch (Exception ${'$'}e) {}
    try { ${'$'}pdo->exec("ALTER TABLE `transactions` ADD COLUMN `depositedAt` BIGINT NULL AFTER `settlementBatchNumber`"); } catch (Exception ${'$'}e) {}
    // Mencegah duplikasi pembayaran pada pelanggan dan periode yang sama
    try { ${'$'}pdo->exec("ALTER TABLE `transactions` ADD UNIQUE KEY `uq_customer_period` (`customerId`, `periodMonth`)"); } catch (Exception ${'$'}e) {}
}

try {
    initTables(${'$'}pdo);
} catch (Exception ${'$'}e) {
    // Abaikan jika tabel sudah siap
}

${'$'}rawInput = file_get_contents('php://input');
${'$'}jsonData = json_decode(${'$'}rawInput, true, 512, JSON_BIGINT_AS_STRING);

${'$'}req_key = ${'$'}_SERVER['HTTP_X_API_KEY'] ?? (${'$'}_POST['api_key'] ?? (${'$'}_GET['api_key'] ?? ''));
if (empty(${'$'}req_key) && is_array(${'$'}jsonData) && isset(${'$'}jsonData['api_key'])) {
    ${'$'}req_key = ${'$'}jsonData['api_key'];
}
if (empty(${'$'}req_key) && isset(${'$'}_SERVER['HTTP_AUTHORIZATION'])) {
    ${'$'}authHeader = ${'$'}_SERVER['HTTP_AUTHORIZATION'];
    if (stripos(${'$'}authHeader, 'Bearer ') === 0) {
        ${'$'}req_key = substr(${'$'}authHeader, 7);
    }
}

${'$'}action = ${'$'}_GET['action'] ?? (${'$'}_POST['action'] ?? '');
if (empty(${'$'}action) && is_array(${'$'}jsonData) && isset(${'$'}jsonData['action'])) {
    ${'$'}action = ${'$'}jsonData['action'];
}

if (${'$'}action === 'get_admin_contact') {
    try {
        ${'$'}stmt = ${'$'}pdo->query("
            SELECT `name`, `phone`, `role` 
            FROM `staff_members` 
            WHERE UPPER(`role`) IN ('OWNER', 'ADMIN') 
              AND `isActive` = 1 
              AND `phone` IS NOT NULL 
              AND TRIM(`phone`) != '' 
            ORDER BY (CASE WHEN UPPER(`role`) = 'OWNER' THEN 1 ELSE 2 END), `id` ASC 
            LIMIT 1
        ");
        ${'$'}admin = ${'$'}stmt->fetch();
        ${'$'}adminPhone = (${'$'}admin && !empty(trim(${'$'}admin['phone']))) ? trim(${'$'}admin['phone']) : '6281584210833';

        echo json_encode([
            'status'     => 'success',
            'code'       => 200,
            'message'    => 'Kontak admin/owner berhasil diambil',
            'phone'      => ${'$'}adminPhone,
            'adminPhone' => ${'$'}adminPhone,
            'data'       => [
                'phone'       => ${'$'}adminPhone,
                'admin_phone' => ${'$'}adminPhone,
                'name'        => ${'$'}admin['name'] ?? 'Admin Netplus',
                'role'        => ${'$'}admin['role'] ?? 'ADMIN'
            ]
        ], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
        exit();
    } catch (Exception ${'$'}e) {
        echo json_encode([
            'status'     => 'success',
            'code'       => 200,
            'phone'      => '6281584210833',
            'adminPhone' => '6281584210833',
            'data'       => [
                'phone'       => '6281584210833',
                'admin_phone' => '6281584210833',
                'name'        => 'Admin Netplus',
                'role'        => 'ADMIN'
            ]
        ]);
        exit();
    }
}

if (${'$'}action !== 'ping' && ${'$'}action !== 'test_connection' && !empty(${'$'}action)) {
    if (!empty(${'$'}api_key) && ${'$'}req_key !== ${'$'}api_key) {
        http_response_code(401);
        echo json_encode([
            'status'  => 'error',
            'code'    => 401,
            'message' => 'Akses ditolak: API Key tidak valid. Silakan periksa pengaturan di aplikasi.'
        ]);
        exit();
    }
}

if (${'$'}action === 'ping' || ${'$'}action === 'test_connection' || empty(${'$'}action)) {
    ${'$'}keyMatch = empty(${'$'}api_key) || (${'$'}req_key === ${'$'}api_key);
    echo json_encode([
        'status'         => 'success',
        'code'           => 200,
        'message'        => 'Koneksi ke MySQL Server Billingku Berhasil!',
        'server_time'    => date('Y-m-d H:i:s'),
        'php_version'    => phpversion(),
        'database'       => ${'$'}db_name,
        'api_key_valid'  => ${'$'}keyMatch,
        'tables_ready'   => true
    ]);
    exit();
}

if (${'$'}action === 'sync_all' || ${'$'}action === 'upload_all') {
    ${'$'}data = ${'$'}jsonData['data'] ?? (${'$'}_POST['data'] ?? []);
    if (is_string(${'$'}data)) {
        ${'$'}data = json_decode(${'$'}data, true);
    }

    if (!is_array(${'$'}data)) {
        echo json_encode([
            'status'  => 'error',
            'message' => 'Format data tidak valid'
        ]);
        exit();
    }

    ${'$'}pdo->beginTransaction();
    try {
        ${'$'}stats = [
            'customers'    => 0,
            'packages'     => 0,
            'transactions' => 0,
            'settlements'  => 0,
            'staff'        => 0,
            'bills'        => 0
        ];

        if (!empty(${'$'}data['customers']) && is_array(${'$'}data['customers'])) {
            ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `customers` 
                (`id`, `customerCode`, `name`, `phone`, `address`, `odpLocation`, `packageId`, `packageName`, `monthlyFee`, `dueDay`, `ipOrPppoe`, `status`, `createdAt`)
                VALUES (:id, :customerCode, :name, :phone, :address, :odpLocation, :packageId, :packageName, :monthlyFee, :dueDay, :ipOrPppoe, :status, :createdAt)
                ON DUPLICATE KEY UPDATE 
                `customerCode` = VALUES(`customerCode`),
                `name` = VALUES(`name`),
                `phone` = VALUES(`phone`),
                `address` = VALUES(`address`),
                `odpLocation` = VALUES(`odpLocation`),
                `packageId` = VALUES(`packageId`),
                `packageName` = VALUES(`packageName`),
                `monthlyFee` = VALUES(`monthlyFee`),
                `dueDay` = VALUES(`dueDay`),
                `ipOrPppoe` = VALUES(`ipOrPppoe`),
                `status` = VALUES(`status`);");

            foreach (${'$'}data['customers'] as ${'$'}c) {
                ${'$'}stmt->execute([
                    ':id'           => ${'$'}c['id'],
                    ':customerCode' => ${'$'}c['customerCode'] ?? '',
                    ':name'         => ${'$'}c['name'] ?? '',
                    ':phone'        => ${'$'}c['phone'] ?? '',
                    ':address'      => ${'$'}c['address'] ?? '',
                    ':odpLocation'  => ${'$'}c['odpLocation'] ?? '',
                    ':packageId'    => ${'$'}c['packageId'] ?? 0,
                    ':packageName'  => ${'$'}c['packageName'] ?? '',
                    ':monthlyFee'   => ${'$'}c['monthlyFee'] ?? 0,
                    ':dueDay'       => ${'$'}c['dueDay'] ?? 10,
                    ':ipOrPppoe'    => ${'$'}c['ipOrPppoe'] ?? '',
                    ':status'       => ${'$'}c['status'] ?? 'ACTIVE',
                    ':createdAt'    => ${'$'}c['createdAt'] ?? round(microtime(true) * 1000)
                ]);
                ${'$'}stats['customers']++;
            }
        }

        if (!empty(${'$'}data['packages']) && is_array(${'$'}data['packages'])) {
            ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `package_plans`
                (`id`, `name`, `speedMbps`, `price`, `description`, `isActive`)
                VALUES (:id, :name, :speedMbps, :price, :description, :isActive)
                ON DUPLICATE KEY UPDATE
                `name` = VALUES(`name`),
                `speedMbps` = VALUES(`speedMbps`),
                `price` = VALUES(`price`),
                `description` = VALUES(`description`),
                `isActive` = VALUES(`isActive`);");

            foreach (${'$'}data['packages'] as ${'$'}p) {
                ${'$'}stmt->execute([
                    ':id'          => ${'$'}p['id'],
                    ':name'        => ${'$'}p['name'] ?? '',
                    ':speedMbps'   => ${'$'}p['speedMbps'] ?? 0,
                    ':price'       => ${'$'}p['price'] ?? 0,
                    ':description' => ${'$'}p['description'] ?? '',
                    ':isActive'    => isset(${'$'}p['isActive']) ? (${'$'}p['isActive'] ? 1 : 0) : 1
                ]);
                ${'$'}stats['packages']++;
            }
        }

        if (!empty(${'$'}data['transactions']) && is_array(${'$'}data['transactions'])) {
            ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `transactions`
                (`id`, `invoiceNumber`, `customerId`, `customerName`, `customerCode`, `packageName`, `monthlyFee`, `adminFee`, `discount`, `totalAmount`, `periodMonth`, `paymentMethod`, `cashierName`, `cashierRole`, `notes`, `paidAt`, `isPrinted`, `isDeposited`, `settlementBatchId`, `settlementBatchNumber`, `depositedAt`)
                VALUES (:id, :invoiceNumber, :customerId, :customerName, :customerCode, :packageName, :monthlyFee, :adminFee, :discount, :totalAmount, :periodMonth, :paymentMethod, :cashierName, :cashierRole, :notes, :paidAt, :isPrinted, :isDeposited, :settlementBatchId, :settlementBatchNumber, :depositedAt)
                ON DUPLICATE KEY UPDATE
                `invoiceNumber` = VALUES(`invoiceNumber`),
                `customerName` = VALUES(`customerName`),
                `totalAmount` = VALUES(`totalAmount`),
                `paymentMethod` = VALUES(`paymentMethod`),
                `isPrinted` = VALUES(`isPrinted`),
                `isDeposited` = VALUES(`isDeposited`),
                `settlementBatchId` = VALUES(`settlementBatchId`),
                `settlementBatchNumber` = VALUES(`settlementBatchNumber`),
                `depositedAt` = VALUES(`depositedAt`);");

            foreach (${'$'}data['transactions'] as ${'$'}t) {
                ${'$'}stmt->execute([
                    ':id'                   => ${'$'}t['id'],
                    ':invoiceNumber'        => ${'$'}t['invoiceNumber'] ?? '',
                    ':customerId'           => ${'$'}t['customerId'] ?? 0,
                    ':customerName'         => ${'$'}t['customerName'] ?? '',
                    ':customerCode'         => ${'$'}t['customerCode'] ?? '',
                    ':packageName'          => ${'$'}t['packageName'] ?? '',
                    ':monthlyFee'           => ${'$'}t['monthlyFee'] ?? 0,
                    ':adminFee'             => ${'$'}t['adminFee'] ?? 0,
                    ':discount'             => ${'$'}t['discount'] ?? 0,
                    ':totalAmount'          => ${'$'}t['totalAmount'] ?? 0,
                    ':periodMonth'          => ${'$'}t['periodMonth'] ?? '',
                    ':paymentMethod'        => ${'$'}t['paymentMethod'] ?? 'TUNAI',
                    ':cashierName'          => ${'$'}t['cashierName'] ?? 'Kasir Utama',
                    ':cashierRole'          => ${'$'}t['cashierRole'] ?? 'KASIR',
                    ':notes'                => ${'$'}t['notes'] ?? '',
                    ':paidAt'               => ${'$'}t['paidAt'] ?? round(microtime(true) * 1000),
                    ':isPrinted'            => isset(${'$'}t['isPrinted']) ? (${'$'}t['isPrinted'] ? 1 : 0) : 0,
                    ':isDeposited'          => isset(${'$'}t['isDeposited']) ? (${'$'}t['isDeposited'] ? 1 : 0) : 0,
                    ':settlementBatchId'    => ${'$'}t['settlementBatchId'] ?? null,
                    ':settlementBatchNumber'=> ${'$'}t['settlementBatchNumber'] ?? null,
                    ':depositedAt'          => ${'$'}t['depositedAt'] ?? null
                ]);
                ${'$'}stats['transactions']++;
            }
        }

        if (!empty(${'$'}data['settlement_batches']) && is_array(${'$'}data['settlement_batches'])) {
            ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `settlement_batches`
                (`id`, `batchNumber`, `periodMonth`, `cashierName`, `recipientName`, `totalAmount`, `totalCashAmount`, `totalNonCashAmount`, `customerCount`, `notes`, `createdAt`)
                VALUES (:id, :batchNumber, :periodMonth, :cashierName, :recipientName, :totalAmount, :totalCashAmount, :totalNonCashAmount, :customerCount, :notes, :createdAt)
                ON DUPLICATE KEY UPDATE
                `batchNumber` = VALUES(`batchNumber`),
                `periodMonth` = VALUES(`periodMonth`),
                `cashierName` = VALUES(`cashierName`),
                `recipientName` = VALUES(`recipientName`),
                `totalAmount` = VALUES(`totalAmount`),
                `customerCount` = VALUES(`customerCount`);");

            foreach (${'$'}data['settlement_batches'] as ${'$'}sb) {
                ${'$'}stmt->execute([
                    ':id'                => ${'$'}sb['id'],
                    ':batchNumber'       => ${'$'}sb['batchNumber'] ?? '',
                    ':periodMonth'       => ${'$'}sb['periodMonth'] ?? '',
                    ':cashierName'       => ${'$'}sb['cashierName'] ?? '',
                    ':recipientName'     => ${'$'}sb['recipientName'] ?? 'Owner / Admin',
                    ':totalAmount'       => ${'$'}sb['totalAmount'] ?? 0,
                    ':totalCashAmount'   => ${'$'}sb['totalCashAmount'] ?? 0,
                    ':totalNonCashAmount'=> ${'$'}sb['totalNonCashAmount'] ?? 0,
                    ':customerCount'     => ${'$'}sb['customerCount'] ?? 0,
                    ':notes'             => ${'$'}sb['notes'] ?? '',
                    ':createdAt'         => ${'$'}sb['createdAt'] ?? round(microtime(true) * 1000)
                ]);
                ${'$'}stats['settlements']++;
            }
        }

        if (!empty(${'$'}data['staff']) && is_array(${'$'}data['staff'])) {
            ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `staff_members`
                (`id`, `username`, `name`, `role`, `phone`, `pin`, `isActive`)
                VALUES (:id, :username, :name, :role, :phone, :pin, :isActive)
                ON DUPLICATE KEY UPDATE
                `name` = VALUES(`name`),
                `role` = VALUES(`role`),
                `phone` = VALUES(`phone`),
                `pin` = VALUES(`pin`),
                `isActive` = VALUES(`isActive`);");

            foreach (${'$'}data['staff'] as ${'$'}s) {
                ${'$'}stmt->execute([
                    ':id'       => ${'$'}s['id'],
                    ':username' => ${'$'}s['username'] ?? '',
                    ':name'     => ${'$'}s['name'] ?? '',
                    ':role'     => ${'$'}s['role'] ?? 'KASIR',
                    ':phone'    => ${'$'}s['phone'] ?? '',
                    ':pin'      => ${'$'}s['pin'] ?? '1234',
                    ':isActive' => isset(${'$'}s['isActive']) ? (${'$'}s['isActive'] ? 1 : 0) : 1
                ]);
                ${'$'}stats['staff']++;
            }
        ${'$'}pdo->commit();

        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Sinkronisasi berhasil! Semua data tersimpan di MySQL server.',
            'stats'   => ${'$'}stats
        ]);
        exit();

    } catch (Exception ${'$'}e) {
        ${'$'}pdo->rollBack();
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menyimpan ke MySQL: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

if (${'$'}action === 'download_all' || ${'$'}action === 'get_all' || ${'$'}action === 'get_all_data') {
    try {
        ${'$'}customers   = ${'$'}pdo->query("SELECT * FROM `customers` ORDER BY `name` ASC")->fetchAll();
        ${'$'}packages    = ${'$'}pdo->query("SELECT * FROM `package_plans` ORDER BY `price` ASC")->fetchAll();
        ${'$'}transactions= ${'$'}pdo->query("SELECT * FROM `transactions` ORDER BY `paidAt` DESC")->fetchAll();
        ${'$'}settlements = ${'$'}pdo->query("SELECT * FROM `settlement_batches` ORDER BY `createdAt` DESC")->fetchAll();
        ${'$'}staff       = ${'$'}pdo->query("SELECT * FROM `staff_members` ORDER BY `name` ASC")->fetchAll();
        ${'$'}announcements = ${'$'}pdo->query("SELECT * FROM `announcements` ORDER BY `createdAt` DESC")->fetchAll();

        // Konversi tipe data boolean dan integer kecil yang aman
        foreach (${'$'}packages as &${'$'}p) {
            ${'$'}p['speedMbps'] = (int)${'$'}p['speedMbps'];
            ${'$'}p['isActive'] = (bool)${'$'}p['isActive'];
        }
        foreach (${'$'}customers as &${'$'}c) {
            ${'$'}c['dueDay'] = (int)${'$'}c['dueDay'];
        }
        foreach (${'$'}transactions as &${'$'}t) {
            ${'$'}t['isPrinted'] = (bool)${'$'}t['isPrinted'];
            ${'$'}t['isDeposited'] = (bool)${'$'}t['isDeposited'];
        }
        foreach (${'$'}settlements as &${'$'}sb) {
            ${'$'}sb['customerCount'] = (int)${'$'}sb['customerCount'];
        }
        foreach (${'$'}staff as &${'$'}s) {
            ${'$'}s['isActive'] = (bool)${'$'}s['isActive'];
        }

        echo json_encode([
            'status' => 'success',
            'code'   => 200,
            'data'   => [
                'customers'          => ${'$'}customers,
                'packages'           => ${'$'}packages,
                'transactions'       => ${'$'}transactions,
                'settlements'        => ${'$'}settlements,
                'settlement_batches' => ${'$'}settlements,
                'staff'              => ${'$'}staff,
                'announcements'      => ${'$'}announcements
            ],
            'counts' => [
                'customers'    => count(${'$'}customers),
                'packages'     => count(${'$'}packages),
                'transactions' => count(${'$'}transactions),
                'settlements'  => count(${'$'}settlements),
                'staff'        => count(${'$'}staff),
                'announcements'=> count(${'$'}announcements)
            ]
        ]);
        exit();

    } catch (Exception ${'$'}e) {
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal mengunduh dari MySQL: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

if (${'$'}action === 'push_transaction') {
    ${'$'}t = ${'$'}jsonData['transaction'] ?? (${'$'}_POST['transaction'] ?? []);
    if (is_string(${'$'}t)) ${'$'}t = json_decode(${'$'}t, true, 512, JSON_BIGINT_AS_STRING);
    if (empty(${'$'}t) && !empty(${'$'}jsonData['id'])) ${'$'}t = ${'$'}jsonData;

    if (empty(${'$'}t) || empty(${'$'}t['id']) || empty(${'$'}t['invoiceNumber'])) {
        http_response_code(400);
        echo json_encode([
            'status'  => 'error',
            'code'    => 400,
            'message' => 'Data transaksi tidak lengkap (ID atau Nomor Invoice wajib diisi)'
        ]);
        exit();
    }

    try {
        // CEK DUPLIKASI PEMBAYARAN: Apakah pelanggan ini sudah pernah dibayar untuk periode yang sama?
        if (!empty(${'$'}t['customerId']) && !empty(${'$'}t['periodMonth'])) {
            ${'$'}checkStmt = ${'$'}pdo->prepare("SELECT `id`, `invoiceNumber`, `customerName`, `periodMonth`, `cashierName`, `paidAt`, `totalAmount` 
                FROM `transactions` 
                WHERE `customerId` = :customerId AND `periodMonth` = :periodMonth 
                LIMIT 1");
            ${'$'}checkStmt->execute([
                ':customerId'  => ${'$'}t['customerId'],
                ':periodMonth' => ${'$'}t['periodMonth']
            ]);
            ${'$'}existingTrx = ${'$'}checkStmt->fetch();

            if (${'$'}existingTrx && (string)${'$'}existingTrx['id'] !== (string)${'$'}t['id']) {
                http_response_code(409); // Conflict: Duplikasi invoice dicegah
                echo json_encode([
                    'status'               => 'duplicate_rejected',
                    'code'                 => 409,
                    'is_duplicate'         => true,
                    'message'              => "Tagihan {${'$'}existingTrx['customerName']} periode {${'$'}existingTrx['periodMonth']} sudah dibayar sebelumnya oleh {${'$'}existingTrx['cashierName']} (No. Faktur: {${'$'}existingTrx['invoiceNumber']}). Pembuatan invoice kedua dicegah!",
                    'existing_invoice'     => ${'$'}existingTrx['invoiceNumber'],
                    'existing_transaction' => ${'$'}existingTrx
                ]);
                exit();
            }
        }

        ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `transactions`
            (`id`, `invoiceNumber`, `customerId`, `customerName`, `customerCode`, `packageName`, `monthlyFee`, `adminFee`, `discount`, `totalAmount`, `periodMonth`, `paymentMethod`, `cashierName`, `cashierRole`, `notes`, `paidAt`, `isPrinted`, `isDeposited`, `settlementBatchId`, `settlementBatchNumber`, `depositedAt`)
            VALUES (:id, :invoiceNumber, :customerId, :customerName, :customerCode, :packageName, :monthlyFee, :adminFee, :discount, :totalAmount, :periodMonth, :paymentMethod, :cashierName, :cashierRole, :notes, :paidAt, :isPrinted, :isDeposited, :settlementBatchId, :settlementBatchNumber, :depositedAt)
            ON DUPLICATE KEY UPDATE
            `customerName` = VALUES(`customerName`),
            `packageName` = VALUES(`packageName`),
            `monthlyFee` = VALUES(`monthlyFee`),
            `adminFee` = VALUES(`adminFee`),
            `discount` = VALUES(`discount`),
            `totalAmount` = VALUES(`totalAmount`),
            `periodMonth` = VALUES(`periodMonth`),
            `paymentMethod` = VALUES(`paymentMethod`),
            `cashierName` = VALUES(`cashierName`),
            `cashierRole` = VALUES(`cashierRole`),
            `notes` = VALUES(`notes`),
            `isPrinted` = VALUES(`isPrinted`),
            `isDeposited` = VALUES(`isDeposited`),
            `settlementBatchId` = VALUES(`settlementBatchId`),
            `settlementBatchNumber` = VALUES(`settlementBatchNumber`),
            `depositedAt` = VALUES(`depositedAt`);");

        ${'$'}stmt->execute([
            ':id'                   => ${'$'}t['id'],
            ':invoiceNumber'        => ${'$'}t['invoiceNumber'],
            ':customerId'           => ${'$'}t['customerId'] ?? 0,
            ':customerName'         => ${'$'}t['customerName'] ?? '',
            ':customerCode'         => ${'$'}t['customerCode'] ?? '',
            ':packageName'          => ${'$'}t['packageName'] ?? '',
            ':monthlyFee'           => ${'$'}t['monthlyFee'] ?? 0,
            ':adminFee'             => ${'$'}t['adminFee'] ?? 0,
            ':discount'             => ${'$'}t['discount'] ?? 0,
            ':totalAmount'          => ${'$'}t['totalAmount'] ?? 0,
            ':periodMonth'          => ${'$'}t['periodMonth'] ?? '',
            ':paymentMethod'        => ${'$'}t['paymentMethod'] ?? 'TUNAI',
            ':cashierName'          => ${'$'}t['cashierName'] ?? 'Kasir Utama',
            ':cashierRole'          => ${'$'}t['cashierRole'] ?? 'KASIR',
            ':notes'                => ${'$'}t['notes'] ?? '',
            ':paidAt'               => ${'$'}t['paidAt'] ?? round(microtime(true) * 1000),
            ':isPrinted'            => isset(${'$'}t['isPrinted']) ? (${'$'}t['isPrinted'] ? 1 : 0) : 0,
            ':isDeposited'          => isset(${'$'}t['isDeposited']) ? (${'$'}t['isDeposited'] ? 1 : 0) : 0,
            ':settlementBatchId'    => ${'$'}t['settlementBatchId'] ?? null,
            ':settlementBatchNumber'=> ${'$'}t['settlementBatchNumber'] ?? null,
            ':depositedAt'          => ${'$'}t['depositedAt'] ?? null
        ]);

        // Otomatis sinkronkan status tagihan di tabel bills ke PAID
        if (!empty(${'$'}t['customerId']) && !empty(${'$'}t['periodMonth'])) {
            ${'$'}stmtMarkPaid = ${'$'}pdo->prepare("UPDATE `bills` 
                SET `status` = 'PAID',
                    `paidAt` = :paidAt,
                    `paymentMethod` = :paymentMethod,
                    `paidVia` = 'CASHIER',
                    `cashierName` = :cashierName,
                    `invoiceNumber` = :invoiceNumber
                WHERE `customerId` = :customerId AND `periodMonth` = :periodMonth AND `status` = 'UNPAID'");
            ${'$'}stmtMarkPaid->execute([
                ':paidAt'        => ${'$'}t['paidAt'] ?? round(microtime(true) * 1000),
                ':paymentMethod' => ${'$'}t['paymentMethod'] ?? 'TUNAI',
                ':cashierName'   => ${'$'}t['cashierName'] ?? 'Kasir Utama',
                ':invoiceNumber' => ${'$'}t['invoiceNumber'] ?? '',
                ':customerId'    => ${'$'}t['customerId'],
                ':periodMonth'   => ${'$'}t['periodMonth']
            ]);
        }

        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Transaksi berhasil disimpan ke database MySQL!'
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        if (${'$'}e->getCode() == 23000 || strpos(${'$'}e->getMessage(), 'Duplicate entry') !== false || strpos(${'$'}e->getMessage(), '1062') !== false) {
            http_response_code(409);
            echo json_encode([
                'status'       => 'duplicate_rejected',
                'code'         => 409,
                'is_duplicate' => true,
                'message'      => "Tagihan periode " . (${'$'}t['periodMonth'] ?? '') . " untuk pelanggan ini sudah pernah dibayar sebelumnya di database. Pembuatan invoice ganda dicegah!"
            ]);
            exit();
        }
        http_response_code(500);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menyimpan transaksi: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// (E) PUSH PELANGGAN TUNGGAL (TAMBAH / EDIT PELANGGAN OTOMATIS)
if (${'$'}action === 'push_customer') {
    ${'$'}c = ${'$'}jsonData['customer'] ?? (${'$'}_POST['customer'] ?? []);
    if (is_string(${'$'}c)) ${'$'}c = json_decode(${'$'}c, true, 512, JSON_BIGINT_AS_STRING);
    if (empty(${'$'}c) && !empty(${'$'}jsonData['id'])) ${'$'}c = ${'$'}jsonData;

    if (empty(${'$'}c) || empty(${'$'}c['id']) || empty(${'$'}c['name'])) {
        http_response_code(400);
        echo json_encode([
            'status'  => 'error',
            'code'    => 400,
            'message' => 'Data pelanggan tidak lengkap (ID dan Nama wajib diisi)'
        ]);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `customers`
            (`id`, `customerCode`, `name`, `phone`, `address`, `odpLocation`, `packageId`, `packageName`, `monthlyFee`, `dueDay`, `ipOrPppoe`, `status`, `createdAt`)
            VALUES (:id, :customerCode, :name, :phone, :address, :odpLocation, :packageId, :packageName, :monthlyFee, :dueDay, :ipOrPppoe, :status, :createdAt)
            ON DUPLICATE KEY UPDATE
            `customerCode` = VALUES(`customerCode`),
            `name` = VALUES(`name`),
            `phone` = VALUES(`phone`),
            `address` = VALUES(`address`),
            `odpLocation` = VALUES(`odpLocation`),
            `packageId` = VALUES(`packageId`),
            `packageName` = VALUES(`packageName`),
            `monthlyFee` = VALUES(`monthlyFee`),
            `dueDay` = VALUES(`dueDay`),
            `ipOrPppoe` = VALUES(`ipOrPppoe`),
            `status` = VALUES(`status`),
            `createdAt` = VALUES(`createdAt`);");

        ${'$'}stmt->execute([
            ':id'           => ${'$'}c['id'],
            ':customerCode' => ${'$'}c['customerCode'] ?? '',
            ':name'         => ${'$'}c['name'],
            ':phone'        => ${'$'}c['phone'] ?? '',
            ':address'      => ${'$'}c['address'] ?? '',
            ':odpLocation'  => ${'$'}c['odpLocation'] ?? '',
            ':packageId'    => ${'$'}c['packageId'] ?? 0,
            ':packageName'  => ${'$'}c['packageName'] ?? '',
            ':monthlyFee'   => ${'$'}c['monthlyFee'] ?? 0,
            ':dueDay'       => ${'$'}c['dueDay'] ?? 10,
            ':ipOrPppoe'    => ${'$'}c['ipOrPppoe'] ?? '',
            ':status'       => ${'$'}c['status'] ?? 'ACTIVE',
            ':createdAt'    => ${'$'}c['createdAt'] ?? round(microtime(true) * 1000)
        ]);

        // Perbarui nominal tagihan berjalan yang belum dibayar (UNPAID) jika tarif atau paket pelanggan diubah
        if (isset(${'$'}c['monthlyFee']) && (float)${'$'}c['monthlyFee'] > 0) {
            ${'$'}stmtUpdateBills = ${'$'}pdo->prepare("UPDATE `bills` 
                SET `amount` = :amount, 
                    `totalAmount` = :amount + `adminFee` - `discount`,
                    `packageName` = :packageName,
                    `customerName` = :customerName
                WHERE `customerId` = :customerId AND `status` = 'UNPAID'");
            ${'$'}stmtUpdateBills->execute([
                ':amount'       => ${'$'}c['monthlyFee'],
                ':packageName'  => ${'$'}c['packageName'] ?? '',
                ':customerName' => ${'$'}c['name'],
                ':customerId'   => ${'$'}c['id']
            ]);
        }

        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Pelanggan berhasil disimpan ke database MySQL!'
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menyimpan pelanggan: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// (F) HAPUS PELANGGAN DARI MYSQL
if (${'$'}action === 'delete_customer') {
    ${'$'}id = ${'$'}jsonData['id'] ?? (${'$'}_POST['id'] ?? (${'$'}_GET['id'] ?? 0));
    if (empty(${'$'}id)) {
        http_response_code(400);
        echo json_encode([
            'status'  => 'error',
            'code'    => 400,
            'message' => 'ID Pelanggan tidak boleh kosong'
        ]);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("DELETE FROM `customers` WHERE `id` = :id");
        ${'$'}stmt->execute([':id' => ${'$'}id]);
        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Pelanggan berhasil dihapus dari MySQL!'
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menghapus pelanggan: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// (G) PUSH PAKET INTERNET TUNGGAL (TAMBAH / EDIT PAKET OTOMATIS)
if (${'$'}action === 'push_package') {
    ${'$'}p = ${'$'}jsonData['package'] ?? (${'$'}_POST['package'] ?? []);
    if (is_string(${'$'}p)) ${'$'}p = json_decode(${'$'}p, true, 512, JSON_BIGINT_AS_STRING);
    if (empty(${'$'}p) && !empty(${'$'}jsonData['id'])) ${'$'}p = ${'$'}jsonData;

    if (empty(${'$'}p) || empty(${'$'}p['id']) || empty(${'$'}p['name'])) {
        http_response_code(400);
        echo json_encode([
            'status'  => 'error',
            'code'    => 400,
            'message' => 'Data paket tidak lengkap (ID dan Nama Paket wajib diisi)'
        ]);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `package_plans`
            (`id`, `name`, `speedMbps`, `price`, `description`, `isActive`)
            VALUES (:id, :name, :speedMbps, :price, :description, :isActive)
            ON DUPLICATE KEY UPDATE
            `name` = VALUES(`name`),
            `speedMbps` = VALUES(`speedMbps`),
            `price` = VALUES(`price`),
            `description` = VALUES(`description`),
            `isActive` = VALUES(`isActive`);");

        ${'$'}stmt->execute([
            ':id'          => ${'$'}p['id'],
            ':name'        => ${'$'}p['name'],
            ':speedMbps'   => ${'$'}p['speedMbps'] ?? 0,
            ':price'       => ${'$'}p['price'] ?? 0,
            ':description' => ${'$'}p['description'] ?? '',
            ':isActive'    => isset(${'$'}p['isActive']) ? (${'$'}p['isActive'] ? 1 : 0) : 1
        ]);

        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Paket internet berhasil disimpan ke database MySQL!'
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menyimpan paket: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// (H) HAPUS PAKET INTERNET DARI MYSQL
if (${'$'}action === 'delete_package') {
    ${'$'}id = ${'$'}jsonData['id'] ?? (${'$'}_POST['id'] ?? (${'$'}_GET['id'] ?? 0));
    if (empty(${'$'}id)) {
        http_response_code(400);
        echo json_encode([
            'status'  => 'error',
            'code'    => 400,
            'message' => 'ID Paket tidak boleh kosong'
        ]);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("DELETE FROM `package_plans` WHERE `id` = :id");
        ${'$'}stmt->execute([':id' => ${'$'}id]);
        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Paket berhasil dihapus dari MySQL!'
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menghapus paket: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// (I) PUSH STAF / KASIR TUNGGAL (TAMBAH / EDIT STAF OTOMATIS)
if (${'$'}action === 'push_staff') {
    ${'$'}s = ${'$'}jsonData['staff'] ?? (${'$'}_POST['staff'] ?? []);
    if (is_string(${'$'}s)) ${'$'}s = json_decode(${'$'}s, true, 512, JSON_BIGINT_AS_STRING);
    if (empty(${'$'}s) && !empty(${'$'}jsonData['id'])) ${'$'}s = ${'$'}jsonData;

    if (empty(${'$'}s) || empty(${'$'}s['id']) || empty(${'$'}s['username'])) {
        http_response_code(400);
        echo json_encode([
            'status'  => 'error',
            'code'    => 400,
            'message' => 'Data staf tidak lengkap (ID dan Username wajib diisi)'
        ]);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `staff_members`
            (`id`, `username`, `name`, `role`, `phone`, `pin`, `isActive`)
            VALUES (:id, :username, :name, :role, :phone, :pin, :isActive)
            ON DUPLICATE KEY UPDATE
            `username` = VALUES(`username`),
            `name` = VALUES(`name`),
            `role` = VALUES(`role`),
            `phone` = VALUES(`phone`),
            `pin` = VALUES(`pin`),
            `isActive` = VALUES(`isActive`);");

        ${'$'}stmt->execute([
            ':id'       => ${'$'}s['id'],
            ':username' => ${'$'}s['username'],
            ':name'     => ${'$'}s['name'] ?? '',
            ':role'     => ${'$'}s['role'] ?? 'KASIR',
            ':phone'    => ${'$'}s['phone'] ?? '',
            ':pin'      => ${'$'}s['pin'] ?? '1234',
            ':isActive' => isset(${'$'}s['isActive']) ? (${'$'}s['isActive'] ? 1 : 0) : 1
        ]);

        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Data staf berhasil disimpan ke database MySQL!'
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menyimpan staf: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// (J) HAPUS STAF DARI MYSQL
if (${'$'}action === 'delete_staff') {
    ${'$'}id = ${'$'}jsonData['id'] ?? (${'$'}_POST['id'] ?? (${'$'}_GET['id'] ?? 0));
    if (empty(${'$'}id)) {
        http_response_code(400);
        echo json_encode([
            'status'  => 'error',
            'code'    => 400,
            'message' => 'ID Staf tidak boleh kosong'
        ]);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("DELETE FROM `staff_members` WHERE `id` = :id");
        ${'$'}stmt->execute([':id' => ${'$'}id]);
        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Staf berhasil dihapus dari MySQL!'
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menghapus staf: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// (K) HAPUS TRANSAKSI DARI MYSQL
if (${'$'}action === 'delete_transaction') {
    ${'$'}id = ${'$'}jsonData['id'] ?? (${'$'}_POST['id'] ?? (${'$'}_GET['id'] ?? 0));
    if (empty(${'$'}id)) {
        http_response_code(400);
        echo json_encode([
            'status'  => 'error',
            'code'    => 400,
            'message' => 'ID Transaksi tidak boleh kosong'
        ]);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("DELETE FROM `transactions` WHERE `id` = :id");
        ${'$'}stmt->execute([':id' => ${'$'}id]);
        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Transaksi berhasil dihapus dari MySQL!'
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menghapus transaksi: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// (L) PUSH SETORAN KASIR TUNGGAL
if (${'$'}action === 'push_settlement') {
    ${'$'}sb = ${'$'}jsonData['settlement_batch'] ?? (${'$'}_POST['settlement_batch'] ?? []);
    if (is_string(${'$'}sb)) ${'$'}sb = json_decode(${'$'}sb, true, 512, JSON_BIGINT_AS_STRING);
    if (empty(${'$'}sb) && !empty(${'$'}jsonData['id'])) ${'$'}sb = ${'$'}jsonData;

    if (empty(${'$'}sb) || empty(${'$'}sb['id']) || empty(${'$'}sb['batchNumber'])) {
        http_response_code(400);
        echo json_encode([
            'status'  => 'error',
            'code'    => 400,
            'message' => 'Data setoran tidak lengkap (ID dan Nomor Setoran wajib diisi)'
        ]);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `settlement_batches`
            (`id`, `batchNumber`, `periodMonth`, `cashierName`, `recipientName`, `totalAmount`, `totalCashAmount`, `totalNonCashAmount`, `customerCount`, `notes`, `createdAt`)
            VALUES (:id, :batchNumber, :periodMonth, :cashierName, :recipientName, :totalAmount, :totalCashAmount, :totalNonCashAmount, :customerCount, :notes, :createdAt)
            ON DUPLICATE KEY UPDATE
            `batchNumber` = VALUES(`batchNumber`),
            `periodMonth` = VALUES(`periodMonth`),
            `cashierName` = VALUES(`cashierName`),
            `recipientName` = VALUES(`recipientName`),
            `totalAmount` = VALUES(`totalAmount`),
            `totalCashAmount` = VALUES(`totalCashAmount`),
            `totalNonCashAmount` = VALUES(`totalNonCashAmount`),
            `customerCount` = VALUES(`customerCount`),
            `notes` = VALUES(`notes`);");

        ${'$'}stmt->execute([
            ':id'                => ${'$'}sb['id'],
            ':batchNumber'       => ${'$'}sb['batchNumber'],
            ':periodMonth'       => ${'$'}sb['periodMonth'] ?? '',
            ':cashierName'       => ${'$'}sb['cashierName'] ?? '',
            ':recipientName'     => ${'$'}sb['recipientName'] ?? 'Owner / Admin',
            ':totalAmount'       => ${'$'}sb['totalAmount'] ?? 0,
            ':totalCashAmount'   => ${'$'}sb['totalCashAmount'] ?? 0,
            ':totalNonCashAmount'=> ${'$'}sb['totalNonCashAmount'] ?? 0,
            ':customerCount'     => ${'$'}sb['customerCount'] ?? 0,
            ':notes'             => ${'$'}sb['notes'] ?? '',
            ':createdAt'         => ${'$'}sb['createdAt'] ?? round(microtime(true) * 1000)
        ]);

        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Data setoran berhasil disimpan ke database MySQL!'
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menyimpan setoran: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// (M) HAPUS SETORAN KASIR DARI MYSQL
if (${'$'}action === 'delete_settlement') {
    ${'$'}id = ${'$'}jsonData['id'] ?? (${'$'}_POST['id'] ?? (${'$'}_GET['id'] ?? 0));
    if (empty(${'$'}id)) {
        http_response_code(400);
        echo json_encode([
            'status'  => 'error',
            'code'    => 400,
            'message' => 'ID Setoran tidak boleh kosong'
        ]);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("DELETE FROM `settlement_batches` WHERE `id` = :id");
        ${'$'}stmt->execute([':id' => ${'$'}id]);
        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Setoran berhasil dihapus dari MySQL!'
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menghapus setoran: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// (N) CEK STATUS PEMBAYARAN PELANGGAN (PRE-FLIGHT CHECK MENCEGAH DOUBLE INVOICE)
if (${'$'}action === 'check_payment_status') {
    ${'$'}cid = ${'$'}_GET['customerId'] ?? (${'$'}_POST['customerId'] ?? (${'$'}jsonData['customerId'] ?? 0));
    ${'$'}pmonth = ${'$'}_GET['periodMonth'] ?? (${'$'}_POST['periodMonth'] ?? (${'$'}jsonData['periodMonth'] ?? ''));

    if (empty(${'$'}cid) || empty(${'$'}pmonth)) {
        http_response_code(400);
        echo json_encode(['status' => 'error', 'code' => 400, 'message' => 'Parameter customerId dan periodMonth diperlukan']);
        exit();
    }

    try {
        // 1. Cek tabel transactions (Kasir)
        ${'$'}stmt = ${'$'}pdo->prepare("SELECT * FROM `transactions` WHERE `customerId` = :cid AND `periodMonth` = :pmonth LIMIT 1");
        ${'$'}stmt->execute([':cid' => ${'$'}cid, ':pmonth' => ${'$'}pmonth]);
        ${'$'}existing = ${'$'}stmt->fetch();

        if (${'$'}existing) {
            ${'$'}existing['isPrinted'] = (bool)${'$'}existing['isPrinted'];
            ${'$'}existing['isDeposited'] = (bool)${'$'}existing['isDeposited'];
            echo json_encode([
                'status'               => 'success',
                'code'                 => 200,
                'is_paid'              => true,
                'paid_via'             => 'CASHIER',
                'cashier_name'         => ${'$'}existing['cashierName'] ?? 'Kasir Utama',
                'invoice_number'       => ${'$'}existing['invoiceNumber'],
                'existing_invoice'     => ${'$'}existing['invoiceNumber'],
                'existing_transaction' => ${'$'}existing,
                'message'              => "Tagihan periode {${'$'}pmonth} sudah lunas di kasir (No Faktur: {${'$'}existing['invoiceNumber']})"
            ]);
            exit();
        }

        // 2. Cek tabel bills jika sudah dibayar via aplikasi pembayaran mandiri atau kanal lain
        ${'$'}stmtBill = ${'$'}pdo->prepare("SELECT * FROM `bills` WHERE `customerId` = :cid AND `periodMonth` = :pmonth LIMIT 1");
        ${'$'}stmtBill->execute([':cid' => ${'$'}cid, ':pmonth' => ${'$'}pmonth]);
        ${'$'}bill = ${'$'}stmtBill->fetch();

        if (${'$'}bill && ${'$'}bill['status'] === 'PAID') {
            echo json_encode([
                'status'               => 'success',
                'code'                 => 200,
                'is_paid'              => true,
                'paid_via'             => ${'$'}bill['paidVia'] ?: 'SELF_PAYMENT',
                'cashier_name'         => ${'$'}bill['cashierName'] ?: 'Aplikasi Pembayaran Mandiri',
                'invoice_number'       => ${'$'}bill['invoiceNumber'] ?: ${'$'}bill['billCode'],
                'existing_invoice'     => ${'$'}bill['invoiceNumber'] ?: ${'$'}bill['billCode'],
                'message'              => "Tagihan periode {${'$'}pmonth} sudah lunas via {${'$'}bill['paidVia']} ({${'$'}bill['billCode']})"
            ]);
            exit();
        }

        echo json_encode([
            'status'   => 'success',
            'code'     => 200,
            'is_paid'  => false,
            'message'  => "Tagihan periode {${'$'}pmonth} belum dibayar"
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode(['status' => 'error', 'code' => 500, 'message' => 'Gagal memeriksa status: ' . ${'$'}e->getMessage()]);
        exit();
    }
}

// (N1) PUSH BILL (SIMPAN / PERBARUI DATA TAGIHAN TUNGGAL)
if (${'$'}action === 'push_bill') {
    ${'$'}b = ${'$'}jsonData['data'] ?? (${'$'}jsonData ?? ${'$'}_POST);
    if (!is_array(${'$'}b) || empty(${'$'}b['customerId'])) {
        http_response_code(400);
        echo json_encode(['status' => 'error', 'code' => 400, 'message' => 'Data tagihan tidak lengkap']);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `bills`
            (`id`, `billCode`, `customerId`, `customerCode`, `customerName`, `packageId`, `packageName`,
             `periodMonth`, `dueDate`, `amount`, `adminFee`, `discount`, `totalAmount`, `status`,
             `createdAt`, `paidAt`, `paymentMethod`, `paidVia`, `cashierName`, `invoiceNumber`, `notes`)
            VALUES (:id, :billCode, :customerId, :customerCode, :customerName, :packageId, :packageName,
             :periodMonth, :dueDate, :amount, :adminFee, :discount, :totalAmount, :status,
             :createdAt, :paidAt, :paymentMethod, :paidVia, :cashierName, :invoiceNumber, :notes)
            ON DUPLICATE KEY UPDATE
            `customerName` = VALUES(`customerName`),
            `packageName`  = VALUES(`packageName`),
            `dueDate`      = VALUES(`dueDate`),
            `amount`       = VALUES(`amount`),
            `adminFee`     = VALUES(`adminFee`),
            `discount`     = VALUES(`discount`),
            `totalAmount`  = VALUES(`totalAmount`),
            `status`       = VALUES(`status`),
            `paidAt`       = VALUES(`paidAt`),
            `paymentMethod`= VALUES(`paymentMethod`),
            `paidVia`      = VALUES(`paidVia`),
            `cashierName`  = VALUES(`cashierName`),
            `invoiceNumber`= VALUES(`invoiceNumber`),
            `notes`        = VALUES(`notes`);");

        ${'$'}stmt->execute([
            ':id'           => ${'$'}b['id'],
            ':billCode'     => ${'$'}b['billCode'] ?? '',
            ':customerId'   => ${'$'}b['customerId'],
            ':customerCode' => ${'$'}b['customerCode'] ?? '',
            ':customerName' => ${'$'}b['customerName'] ?? '',
            ':packageId'    => ${'$'}b['packageId'] ?? 0,
            ':packageName'  => ${'$'}b['packageName'] ?? '',
            ':periodMonth'  => ${'$'}b['periodMonth'] ?? '',
            ':dueDate'      => ${'$'}b['dueDate'] ?? 0,
            ':amount'       => ${'$'}b['amount'] ?? 0,
            ':adminFee'     => ${'$'}b['adminFee'] ?? 0,
            ':discount'     => ${'$'}b['discount'] ?? 0,
            ':totalAmount'  => ${'$'}b['totalAmount'] ?? 0,
            ':status'       => ${'$'}b['status'] ?? 'UNPAID',
            ':createdAt'    => ${'$'}b['createdAt'] ?? round(microtime(true) * 1000),
            ':paidAt'       => ${'$'}b['paidAt'] ?? null,
            ':paymentMethod'=> ${'$'}b['paymentMethod'] ?? '',
            ':paidVia'      => ${'$'}b['paidVia'] ?? '',
            ':cashierName'  => ${'$'}b['cashierName'] ?? '',
            ':invoiceNumber'=> ${'$'}b['invoiceNumber'] ?? '',
            ':notes'        => ${'$'}b['notes'] ?? ''
        ]);

        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Data tagihan berhasil disimpan di MySQL!',
            'id'      => ${'$'}b['id']
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode(['status' => 'error', 'code' => 500, 'message' => 'Gagal menyimpan tagihan: ' . ${'$'}e->getMessage()]);
        exit();
    }
}

// (N2) GET UNPAID BILLS (DIBACA OLEH APLIKASI KASIR MAUPUN PEMBAYARAN MANDIRI)
if (${'$'}action === 'get_unpaid_bills') {
    try {
        ${'$'}bills = ${'$'}pdo->query("SELECT * FROM `bills` WHERE `status` = 'UNPAID' ORDER BY `dueDate` ASC, `id` ASC")->fetchAll();
        echo json_encode([
            'status' => 'success',
            'code'   => 200,
            'data'   => ${'$'}bills,
            'count'  => count(${'$'}bills)
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode(['status' => 'error', 'code' => 500, 'message' => 'Gagal mengambil data tagihan: ' . ${'$'}e->getMessage()]);
        exit();
    }
}

// (N3) GET CUSTOMER BILLS (DIBACA OLEH APLIKASI PEMBAYARAN MANDIRI PELANGGAN)
if (${'$'}action === 'get_customer_bills') {
    ${'$'}code = ${'$'}_GET['customerCode'] ?? (${'$'}jsonData['customerCode'] ?? '');
    ${'$'}phone = ${'$'}_GET['phone'] ?? (${'$'}jsonData['phone'] ?? '');
    ${'$'}cid = ${'$'}_GET['customerId'] ?? (${'$'}jsonData['customerId'] ?? 0);

    try {
        if (!empty(${'$'}cid)) {
            ${'$'}stmt = ${'$'}pdo->prepare("SELECT * FROM `bills` WHERE `customerId` = :cid ORDER BY `dueDate` DESC");
            ${'$'}stmt->execute([':cid' => ${'$'}cid]);
        } elseif (!empty(${'$'}code)) {
            ${'$'}stmt = ${'$'}pdo->prepare("SELECT * FROM `bills` WHERE `customerCode` = :code ORDER BY `dueDate` DESC");
            ${'$'}stmt->execute([':code' => ${'$'}code]);
        } elseif (!empty(${'$'}phone)) {
            ${'$'}stmt = ${'$'}pdo->prepare("SELECT b.* FROM `bills` b JOIN `customers` c ON b.customerId = c.id WHERE c.phone = :phone ORDER BY b.dueDate DESC");
            ${'$'}stmt->execute([':phone' => ${'$'}phone]);
        } else {
            http_response_code(400);
            echo json_encode(['status' => 'error', 'code' => 400, 'message' => 'Masukkan customerCode, phone, atau customerId']);
            exit();
        }

        ${'$'}bills = ${'$'}stmt->fetchAll();
        echo json_encode([
            'status' => 'success',
            'code'   => 200,
            'data'   => ${'$'}bills,
            'count'  => count(${'$'}bills)
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode(['status' => 'error', 'code' => 500, 'message' => 'Gagal mengambil tagihan pelanggan: ' . ${'$'}e->getMessage()]);
        exit();
    }
}

// (N4) PAY BILL (DIGUNAKAN OLEH APLIKASI PEMBAYARAN MANDIRI MAUPUN KASIR DENGAN LOCKING ATOMIK)
if (${'$'}action === 'pay_bill') {
    ${'$'}billId = ${'$'}jsonData['billId'] ?? (${'$'}_POST['billId'] ?? 0);
    ${'$'}paymentMethod = ${'$'}jsonData['paymentMethod'] ?? (${'$'}_POST['paymentMethod'] ?? 'MANDIRI_ONLINE');
    ${'$'}paidVia = ${'$'}jsonData['paidVia'] ?? (${'$'}_POST['paidVia'] ?? 'SELF_PAYMENT');
    ${'$'}cashierName = ${'$'}jsonData['cashierName'] ?? (${'$'}_POST['cashierName'] ?? 'Aplikasi Pembayaran Mandiri');
    ${'$'}invoiceNumber = ${'$'}jsonData['invoiceNumber'] ?? (${'$'}_POST['invoiceNumber'] ?? '');
    ${'$'}now = round(microtime(true) * 1000);

    if (empty(${'$'}billId)) {
        http_response_code(400);
        echo json_encode(['status' => 'error', 'code' => 400, 'message' => 'billId diperlukan']);
        exit();
    }

    if (empty(${'$'}invoiceNumber)) {
        ${'$'}invoiceNumber = 'INV-' . date('Ymd-His') . '-' . rand(100, 999);
    }

    ${'$'}pdo->beginTransaction();
    try {
        ${'$'}stmt = ${'$'}pdo->prepare("SELECT * FROM `bills` WHERE `id` = :id FOR UPDATE");
        ${'$'}stmt->execute([':id' => ${'$'}billId]);
        ${'$'}bill = ${'$'}stmt->fetch();

        if (!${'$'}bill) {
            ${'$'}pdo->rollBack();
            http_response_code(404);
            echo json_encode(['status' => 'error', 'code' => 404, 'message' => 'Tagihan tidak ditemukan']);
            exit();
        }

        if (${'$'}bill['status'] === 'PAID') {
            ${'$'}pdo->rollBack();
            http_response_code(409);
            echo json_encode([
                'status'         => 'error',
                'code'           => 409,
                'is_double_pay'  => true,
                'message'        => 'Tagihan ini SUDAH LUNAS sebelumnya! Pembayaran ganda dicegah.',
                'invoiceNumber'  => ${'$'}bill['invoiceNumber'],
                'paidAt'         => ${'$'}bill['paidAt'],
                'paidVia'        => ${'$'}bill['paidVia']
            ]);
            exit();
        }

        // Update tagihan menjadi PAID
        ${'$'}updateStmt = ${'$'}pdo->prepare("UPDATE `bills` SET 
            `status` = 'PAID',
            `paidAt` = :paidAt,
            `paymentMethod` = :paymentMethod,
            `paidVia` = :paidVia,
            `cashierName` = :cashierName,
            `invoiceNumber` = :invoiceNumber
            WHERE `id` = :id");
        ${'$'}updateStmt->execute([
            ':id'            => ${'$'}billId,
            ':paidAt'        => ${'$'}now,
            ':paymentMethod' => ${'$'}paymentMethod,
            ':paidVia'       => ${'$'}paidVia,
            ':cashierName'   => ${'$'}cashierName,
            ':invoiceNumber' => ${'$'}invoiceNumber
        ]);

        // Simpan juga ke transactions agar laporan pendapatan terpusat sinkron
        ${'$'}trxId = round(microtime(true) * 1000) + rand(1, 999);
        ${'$'}insertTrx = ${'$'}pdo->prepare("INSERT INTO `transactions` 
            (`id`, `invoiceNumber`, `customerId`, `customerName`, `customerCode`, `packageName`, `monthlyFee`,
             `adminFee`, `discount`, `totalAmount`, `periodMonth`, `paymentMethod`, `cashierName`, `cashierRole`,
             `notes`, `paidAt`, `isPrinted`, `isDeposited`)
            VALUES (:id, :inv, :cid, :cname, :ccode, :pname, :fee, :admin, :disc, :total, :pmonth, :pmethod, :cby, :role, :notes, :pat, 0, 0)
            ON DUPLICATE KEY UPDATE `invoiceNumber` = VALUES(`invoiceNumber`)");
        ${'$'}insertTrx->execute([
            ':id'      => ${'$'}trxId,
            ':inv'     => ${'$'}invoiceNumber,
            ':cid'     => ${'$'}bill['customerId'],
            ':cname'   => ${'$'}bill['customerName'],
            ':ccode'   => ${'$'}bill['customerCode'],
            ':pname'   => ${'$'}bill['packageName'],
            ':fee'     => ${'$'}bill['amount'],
            ':admin'   => ${'$'}bill['adminFee'],
            ':disc'    => ${'$'}bill['discount'],
            ':total'   => ${'$'}bill['totalAmount'],
            ':pmonth'  => ${'$'}bill['periodMonth'],
            ':pmethod' => ${'$'}paymentMethod,
            ':cby'     => ${'$'}cashierName,
            ':role'    => (${'$'}paidVia === 'CASHIER' ? 'KASIR' : 'SELF_PAYMENT'),
            ':notes'   => 'Lunas via ' . ${'$'}paidVia,
            ':pat'     => ${'$'}now
        ]);

        ${'$'}pdo->commit();

        echo json_encode([
            'status'        => 'success',
            'code'          => 200,
            'message'       => 'Pembayaran berhasil dikonfirmasi dan tagihan lunas!',
            'billId'        => ${'$'}billId,
            'invoiceNumber' => ${'$'}invoiceNumber,
            'paidAt'        => ${'$'}now
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        ${'$'}pdo->rollBack();
        http_response_code(500);
        echo json_encode(['status' => 'error', 'code' => 500, 'message' => 'Gagal memproses pembayaran: ' . ${'$'}e->getMessage()]);
        exit();
    }
}

// (N5) DELETE BILL
if (${'$'}action === 'delete_bill') {
    ${'$'}id = ${'$'}jsonData['id'] ?? (${'$'}_POST['id'] ?? (${'$'}_GET['id'] ?? 0));
    if (empty(${'$'}id)) {
        http_response_code(400);
        echo json_encode(['status' => 'error', 'code' => 400, 'message' => 'ID Tagihan tidak boleh kosong']);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("DELETE FROM `bills` WHERE `id` = :id");
        ${'$'}stmt->execute([':id' => ${'$'}id]);
        echo json_encode(['status' => 'success', 'code' => 200, 'message' => 'Tagihan berhasil dihapus dari MySQL!']);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode(['status' => 'error', 'code' => 500, 'message' => 'Gagal menghapus tagihan: ' . ${'$'}e->getMessage()]);
        exit();
    }
}

// (N6) GENERATE MONTHLY BILLS (SINKRONISASI TAGIHAN AWAL BULAN / CRON SERVER HOSTING)
if (${'$'}action === 'generate_bills' || ${'$'}action === 'cron_generate_bills') {
    ${'$'}targetPeriod = ${'$'}_GET['period'] ?? (${'$'}jsonData['period'] ?? '');
    ${'$'}adminFee = (int)(${'$'}_GET['adminFee'] ?? (${'$'}jsonData['adminFee'] ?? 0));

    // Jika period tidak dispesifikasikan, gunakan format Indonesia bulan berjalan
    if (empty(${'$'}targetPeriod)) {
        ${'$'}indoMonths = [
            1 => 'Januari', 2 => 'Februari', 3 => 'Maret', 4 => 'April',
            5 => 'Mei', 6 => 'Juni', 7 => 'Juli', 8 => 'Agustus',
            9 => 'September', 10 => 'Oktober', 11 => 'November', 12 => 'Desember'
        ];
        ${'$'}m = (int)date('n');
        ${'$'}y = date('Y');
        ${'$'}targetPeriod = (${'$'}indoMonths[${'$'}m] ?? date('F')) . ' ' . ${'$'}y;
    }

    try {
        // Ambil semua pelanggan aktif & terisolir
        ${'$'}custStmt = ${'$'}pdo->query("SELECT * FROM `customers` WHERE `status` IN ('ACTIVE', 'ISOLATED') ORDER BY `id` ASC");
        ${'$'}custs = ${'$'}custStmt->fetchAll();

        ${'$'}generated = 0;
        ${'$'}skipped = 0;
        ${'$'}curMonthCode = date('Ym');
        ${'$'}curYear = (int)date('Y');
        ${'$'}curMonthNum = (int)date('n');

        foreach (${'$'}custs as ${'$'}c) {
            // Cek apakah tagihan untuk periode ini sudah ada
            ${'$'}checkStmt = ${'$'}pdo->prepare("SELECT `id`, `status` FROM `bills` WHERE `customerId` = :cid AND `periodMonth` = :pmonth LIMIT 1");
            ${'$'}checkStmt->execute([':cid' => ${'$'}c['id'], ':pmonth' => ${'$'}targetPeriod]);
            if (${'$'}checkStmt->fetch()) {
                ${'$'}skipped++;
                continue;
            }

            // Cek apakah sudah pernah dibayar di transactions
            ${'$'}trxCheck = ${'$'}pdo->prepare("SELECT `id`, `invoiceNumber`, `paidAt`, `paymentMethod`, `cashierName` FROM `transactions` WHERE `customerId` = :cid AND `periodMonth` = :pmonth LIMIT 1");
            ${'$'}trxCheck->execute([':cid' => ${'$'}c['id'], ':pmonth' => ${'$'}targetPeriod]);
            ${'$'}existingTrx = ${'$'}trxCheck->fetch();

            ${'$'}dueDay = isset(${'$'}c['dueDay']) ? (int)${'$'}c['dueDay'] : 10;
            if (${'$'}dueDay < 1) ${'$'}dueDay = 10;
            if (${'$'}dueDay > 28) ${'$'}dueDay = 28;

            ${'$'}dueTimestamp = mktime(23, 59, 59, ${'$'}curMonthNum, ${'$'}dueDay, ${'$'}curYear) * 1000;
            ${'$'}billId = round(microtime(true) * 1000) + rand(100, 9999);
            ${'$'}billCode = 'BILL-' . (${'$'}c['customerCode'] ?: 'CUST') . '-' . ${'$'}curMonthCode;
            ${'$'}fee = (int)(${'$'}c['monthlyFee'] ?? 0);
            ${'$'}total = ${'$'}fee + ${'$'}adminFee;

            ${'$'}isPaid = !empty(${'$'}existingTrx);

            ${'$'}insStmt = ${'$'}pdo->prepare("INSERT INTO `bills`
                (`id`, `billCode`, `customerId`, `customerCode`, `customerName`, `packageId`, `packageName`,
                 `periodMonth`, `dueDate`, `amount`, `adminFee`, `discount`, `totalAmount`, `status`,
                 `createdAt`, `paidAt`, `paymentMethod`, `paidVia`, `cashierName`, `invoiceNumber`, `notes`)
                VALUES (:id, :bcode, :cid, :ccode, :cname, :pid, :pname,
                        :pmonth, :ddate, :amt, :adm, 0, :total, :st,
                        :cat, :pat, :pmethod, :pvia, :cby, :inv, :notes)");

            ${'$'}insStmt->execute([
                ':id'          => ${'$'}billId,
                ':bcode'       => ${'$'}billCode,
                ':cid'         => ${'$'}c['id'],
                ':ccode'       => ${'$'}c['customerCode'] ?? '',
                ':cname'       => ${'$'}c['name'] ?? '',
                ':pid'         => ${'$'}c['packageId'] ?? 0,
                ':pname'       => ${'$'}c['packageName'] ?? '',
                ':pmonth'      => ${'$'}targetPeriod,
                ':ddate'       => ${'$'}dueTimestamp,
                ':amt'         => ${'$'}fee,
                ':adm'         => ${'$'}adminFee,
                ':total'       => ${'$'}total,
                ':st'          => ${'$'}isPaid ? 'PAID' : 'UNPAID',
                ':cat'         => round(microtime(true) * 1000),
                ':pat'         => ${'$'}isPaid ? (${'$'}existingTrx['paidAt'] ?? null) : null,
                ':pmethod'     => ${'$'}isPaid ? (${'$'}existingTrx['paymentMethod'] ?? 'TUNAI') : '',
                ':pvia'        => ${'$'}isPaid ? 'CASHIER' : '',
                ':cby'         => ${'$'}isPaid ? (${'$'}existingTrx['cashierName'] ?? 'Kasir') : '',
                ':inv'         => ${'$'}isPaid ? (${'$'}existingTrx['invoiceNumber'] ?? null) : null,
                ':notes'       => ${'$'}isPaid ? 'Lunas tercatat otomatis' : 'Tagihan awal bulan'
            ]);

            ${'$'}generated++;
        }

        echo json_encode([
            'status'    => 'success',
            'code'      => 200,
            'message'   => "Penerbitan tagihan awal bulan ({${'$'}targetPeriod}) selesai: {${'$'}generated} tagihan baru diterbitkan, {${'$'}skipped} sudah ada sebelumnya.",
            'period'    => ${'$'}targetPeriod,
            'generated' => ${'$'}generated,
            'skipped'   => ${'$'}skipped,
            'total'     => count(${'$'}custs)
        ]);
        exit();

    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'Gagal menerbitkan tagihan awal bulan: ' . ${'$'}e->getMessage()
        ]);
        exit();
    }
}

// =============================================================
// (O) ENDPOINT PENGUMUMAN & NOTIFIKASI GANGGUAN (UNTUK NET-PAY)
// =============================================================

// (O1) GET ANNOUNCEMENTS (DIBACA OLEH APLIKASI PELANGGAN NET-PAY)
if (${'$'}action === 'get_announcements') {
    ${'$'}filterStatus = ${'$'}_GET['status'] ?? (${'$'}jsonData['status'] ?? 'ACTIVE');
    ${'$'}scope = ${'$'}_GET['scope'] ?? (${'$'}jsonData['scope'] ?? '');

    try {
        if (${'$'}filterStatus === 'ALL') {
            if (!empty(${'$'}scope)) {
                ${'$'}stmt = ${'$'}pdo->prepare("SELECT * FROM `announcements` WHERE `targetScope` = 'SEMUA' OR `targetScope` = :scope ORDER BY `createdAt` DESC");
                ${'$'}stmt->execute([':scope' => ${'$'}scope]);
            } else {
                ${'$'}stmt = ${'$'}pdo->query("SELECT * FROM `announcements` ORDER BY `createdAt` DESC");
            }
        } else {
            if (!empty(${'$'}scope)) {
                ${'$'}stmt = ${'$'}pdo->prepare("SELECT * FROM `announcements` WHERE `status` = :st AND (`targetScope` = 'SEMUA' OR `targetScope` = :scope) ORDER BY `createdAt` DESC");
                ${'$'}stmt->execute([':st' => ${'$'}filterStatus, ':scope' => ${'$'}scope]);
            } else {
                ${'$'}stmt = ${'$'}pdo->prepare("SELECT * FROM `announcements` WHERE `status` = :st ORDER BY `createdAt` DESC");
                ${'$'}stmt->execute([':st' => ${'$'}filterStatus]);
            }
        }

        ${'$'}rows = ${'$'}stmt->fetchAll();
        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'count'   => count(${'$'}rows),
            'message' => 'Daftar pengumuman berhasil diambil',
            'data'    => ${'$'}rows
        ], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode(['status' => 'error', 'code' => 500, 'message' => 'Gagal mengambil pengumuman: ' . ${'$'}e->getMessage()]);
        exit();
    }
}

// (O2) PUSH ANNOUNCEMENT (DIPUBLIKASIKAN DARI APLIKASI BILLINGKU KASIR/ADMIN)
if (${'$'}action === 'push_announcement') {
    ${'$'}ann = ${'$'}jsonData['announcement'] ?? (${'$'}_POST['announcement'] ?? []);
    if (is_string(${'$'}ann)) ${'$'}ann = json_decode(${'$'}ann, true, 512, JSON_BIGINT_AS_STRING);
    if (empty(${'$'}ann) && !empty(${'$'}jsonData['id'])) ${'$'}ann = ${'$'}jsonData;

    if (empty(${'$'}ann) || empty(${'$'}ann['id']) || empty(${'$'}ann['title'])) {
        http_response_code(400);
        echo json_encode(['status' => 'error', 'code' => 400, 'message' => 'Data pengumuman tidak valid (ID dan Judul wajib diisi)']);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO `announcements`
            (`id`, `title`, `category`, `message`, `targetScope`, `status`, `estimatedEnd`, `createdBy`, `createdAt`)
            VALUES (:id, :title, :category, :message, :targetScope, :status, :estimatedEnd, :createdBy, :createdAt)
            ON DUPLICATE KEY UPDATE
            `title` = VALUES(`title`),
            `category` = VALUES(`category`),
            `message` = VALUES(`message`),
            `targetScope` = VALUES(`targetScope`),
            `status` = VALUES(`status`),
            `estimatedEnd` = VALUES(`estimatedEnd`),
            `createdBy` = VALUES(`createdBy`);");

        ${'$'}stmt->execute([
            ':id'           => ${'$'}ann['id'],
            ':title'        => ${'$'}ann['title'],
            ':category'     => ${'$'}ann['category'] ?? 'INFO',
            ':message'      => ${'$'}ann['message'] ?? '',
            ':targetScope'  => ${'$'}ann['targetScope'] ?? 'SEMUA',
            ':status'       => ${'$'}ann['status'] ?? 'ACTIVE',
            ':estimatedEnd' => ${'$'}ann['estimatedEnd'] ?? '',
            ':createdBy'    => ${'$'}ann['createdBy'] ?? 'Admin',
            ':createdAt'    => ${'$'}ann['createdAt'] ?? round(microtime(true) * 1000)
        ]);

        echo json_encode([
            'status'  => 'success',
            'code'    => 200,
            'message' => 'Pengumuman berhasil disinkronkan ke server untuk aplikasi Net-pay!'
        ]);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode(['status' => 'error', 'code' => 500, 'message' => 'Gagal menyimpan pengumuman: ' . ${'$'}e->getMessage()]);
        exit();
    }
}

// (O3) HAPUS PENGUMUMAN DARI SERVER
if (${'$'}action === 'delete_announcement') {
    ${'$'}id = ${'$'}jsonData['id'] ?? (${'$'}_POST['id'] ?? (${'$'}_GET['id'] ?? 0));
    if (empty(${'$'}id)) {
        http_response_code(400);
        echo json_encode(['status' => 'error', 'code' => 400, 'message' => 'ID Pengumuman wajib diisi']);
        exit();
    }

    try {
        ${'$'}stmt = ${'$'}pdo->prepare("DELETE FROM `announcements` WHERE `id` = :id");
        ${'$'}stmt->execute([':id' => ${'$'}id]);
        echo json_encode(['status' => 'success', 'code' => 200, 'message' => 'Pengumuman berhasil dihapus dari server!']);
        exit();
    } catch (Exception ${'$'}e) {
        http_response_code(500);
        echo json_encode(['status' => 'error', 'code' => 500, 'message' => 'Gagal menghapus pengumuman: ' . ${'$'}e->getMessage()]);
        exit();
    }
}

http_response_code(400);
echo json_encode([
    'status'  => 'error',
    'code'    => 400,
    'message' => 'Aksi (' . htmlspecialchars(${'$'}action) . ') tidak dikenali oleh billingku.php.'
]);
exit();
?>
    """.trimIndent()
}
