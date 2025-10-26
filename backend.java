// Dapatkan elemen canvas dan konteks 2D-nya
const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');

// --- PENGATURAN DASAR ---

// Gravitasi. Nilai positif menarik ke bawah.
const GRAVITASI = 0.5;
// Lantai tempat player berdiri
const LANTAI_Y = canvas.height - 50; 
let levelSekarang = 1;

// --- OBJEK PLAYER ---
const player = {
    x: 100,
    y: LANTAI_Y - 50, // Mulai di atas lantai
    width: 40,
    height: 60,
    warna: '#00bfff', // Biru cerah
    
    // Kecepatan
    speed: 5,
    velocityX: 0,
    velocityY: 0,
    
    // Lompat
    kekuatanLompat: 12,
    bisaLompat: true,
    lompatanTersisa: 2, // Untuk double jump
    
    // Dash
    bisaDash: true,
    sedangDash: false,
    dashTimer: 0,
    dashDurasi: 0.15, // durasi dash dalam detik
    dashSpeed: 20,
    dashCooldown: 0.5, // waktu tunggu setelah dash
    dashCooldownTimer: 0,

    // Parry
    bisaParry: true,
    sedangParry: false,
    parryTimer: 0,
    parryDurasi: 0.2, // Jendela waktu parry (sangat singkat)
    parryCooldown: 1,
    parryCooldownTimer: 0,

    // Status
    diLantai: false,
    health: 100
};

// --- OBJEK MUSUH ---
// Kita buat satu musuh sebagai contoh
const musuh = {
    x: 700,
    y: LANTAI_Y - 80,
    width: 60,
    height: 80,
    warna: '#e63946', // Merah
    health: 50,
    
    // AI Sederhana: Serang setiap bbrp detik
    sedangSerang: false,
    attackTimer: 0,
    attackCooldown: 2, // Serang setiap 2 detik
    attackDurasi: 0.5, // Durasi serangan
    attackBox: { // Hitbox serangan
        x: 0,
        y: 0,
        width: 80,
        height: 20
    }
};

// --- PORTAL (Untuk pindah level) ---
const portal = {
    x: canvas.width - 60,
    y: LANTAI_Y - 60,
    width: 20,
    height: 60,
    warna: '#9a00ff'
};

// --- INPUT HANDLER ---
// Objek untuk melacak tombol apa yang sedang ditekan
const keys = {
    a: { pressed: false }, // Kiri
    d: { pressed: false }, // Kanan
    w: { pressed: false }, // Lompat (atau spasi)
    j: { pressed: false }, // Parry (atau 'k')
    k: { pressed: false }, // Dash (atau 'l')
};

// --- GAME LOOP ---
let lastTime = 0;

function gameLoop(timestamp) {
    // Hitung deltaTime (waktu antar frame) untuk gerakan yang konsisten
    const deltaTime = (timestamp - lastTime) / 1000; // dalam detik
    lastTime = timestamp;

    // 1. UPDATE (Logika Game)
    update(deltaTime);
    
    // 2. DRAW (Menggambar di Canvas)
    draw();

    // Meminta frame berikutnya
    requestAnimationFrame(gameLoop);
}

// --- FUNGSI UPDATE (Logika) ---
function update(dt) {
    // Reset warna player
    player.warna = '#00bfff';

    // --- Update Cooldown Timer ---
    if (player.dashCooldownTimer > 0) player.dashCooldownTimer -= dt;
    if (player.parryCooldownTimer > 0) player.parryCooldownTimer -= dt;

    // --- Logika Dash ---
    if (player.sedangDash) {
        player.parryTimer = 0; // Hentikan parry jika sedang dash
        player.velocityY = 0; // Dash mengabaikan gravitasi
        player.warna = '#ffffff'; // Warna putih saat dash
        player.dashTimer -= dt;
        if (player.dashTimer <= 0) {
            player.sedangDash = false;
            player.velocityX = 0;
        }
    } else {
        // --- Logika Gerakan Normal (jika tidak dash) ---
        // Gravitasi
        player.velocityY += GRAVITASI;
        
        // Gerakan Kiri/Kanan
        player.velocityX = 0;
        if (keys.a.pressed) {
            player.velocityX = -player.speed;
        }
        if (keys.d.pressed) {
            player.velocityX = player.speed;
        }
    }

    // --- Logika Parry ---
    if (player.sedangParry) {
        player.warna = '#ffd700'; // Warna emas saat parry
        player.parryTimer -= dt;
        if (player.parryTimer <= 0) {
            player.sedangParry = false;
        }
    }

    // Terapkan kecepatan ke posisi
    player.x += player.velocityX * (player.sedangDash ? player.dashSpeed : 1);
    player.y += player.velocityY;
    
    // --- Cek Batasan ---
    // Cek tabrakan dengan lantai
    if (player.y + player.height > LANTAI_Y) {
        player.y = LANTAI_Y - player.height;
        player.velocityY = 0;
        player.diLantai = true;
        player.lompatanTersisa = 2; // Reset double jump
    } else {
        player.diLantai = false;
    }

    // Cek batas kiri/kanan canvas
    if (player.x < 0) player.x = 0;
    if (player.x + player.width > canvas.width) {
        player.x = canvas.width - player.width;
    }

    // --- Update Musuh ---
    updateMusuh(dt);

    // --- Cek Kollisi ---
    cekKollisi();
}

// --- FUNGSI UPDATE MUSUH (AI Sederhana) ---
function updateMusuh(dt) {
    musuh.attackTimer -= dt;

    if (musuh.attackTimer <= 0 && !musuh.sedangSerang) {
        // Waktunya menyerang! (Timing Attack)
        musuh.sedangSerang = true;
        musuh.attackTimer = musuh.attackDurasi; // Waktu serangan aktif
        
        // Tentukan hitbox serangan (misal, tebasan di depannya)
        musuh.attackBox.x = musuh.x - musuh.attackBox.width; // Serang ke kiri
        musuh.attackBox.y = musuh.y + 20;

    } else if (musuh.sedangSerang && musuh.attackTimer <= 0) {
        // Serangan selesai, masuk mode cooldown
        musuh.sedangSerang = false;
        musuh.attackTimer = musuh.attackCooldown; // Waktu tunggu sblm serang lagi
    }
}

// --- FUNGSI CEK KOLLISI ---
function cekKollisi() {
    // Kollisi Serangan Musuh dengan Player
    if (musuh.sedangSerang && 
        tabrakan(musuh.attackBox, player)) 
    {
        if (player.sedangParry) {
            // BERHASIL PARRY!
            console.log("PARRIED!");
            musuh.sedangSerang = false; // Batalkan serangan musuh
            musuh.attackTimer = musuh.attackCooldown + 2; // Stun musuh (cooldown lebih lama)
            // Di sini Anda bisa menambahkan efek visual parry
        } else if (!player.sedangDash) { 
            // Player kena hit (Dash = invulnerable)
            console.log("Player Kena Hit!");
            player.health -= 10;
            // Di sini Anda bisa tambahkan efek visual kena hit
        }
    }

    // Kollisi Player dengan Portal
    if (tabrakan(player, portal)) {
        gantiLevel();
    }
}

// --- FUNGSI GANTI LEVEL ---
function gantiLevel() {
    levelSekarang++;
    console.log("Masuk ke Level " + levelSekarang);
    
    // Pindahkan player ke awal
    player.x = 100;
    player.y = LANTAI_Y - 50;

    // "Variasi" musuh (misal: lebih kuat atau beda posisi)
    musuh.x = 600 + Math.random() * 100;
    musuh.health = 50 + (levelSekarang * 10);
    musuh.attackCooldown = Math.max(0.5, 2 - (levelSekarang * 0.1)); // Musuh makin cepat

    // Latar belakang interaktif/berbeda
    // Di fungsi draw(), kita akan ubah warna berdasarkan levelSekarang
}


// --- FUNGSI DRAW (Render) ---
function draw() {
    // Latar Belakang Bervariasi (sesuai permintaan)
    // Ini cara sederhana, idealnya menggunakan gambar
    switch(levelSekarang % 3) {
        case 1:
            ctx.fillStyle = '#2c3e50'; // Area 1: Malam
            break;
        case 2:
            ctx.fillStyle = '#4a2c50'; // Area 2: Gua Ungu
            break;
        case 0:
            ctx.fillStyle = '#502c2c'; // Area 3: Area Vulkanik
            break;
    }
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // Gambar Lantai
    ctx.fillStyle = '#4f4f4f';
    ctx.fillRect(0, LANTAI_Y, canvas.width, canvas.height - LANTAI_Y);

    // Gambar Portal
    ctx.fillStyle = portal.warna;
    ctx.fillRect(portal.x, portal.y, portal.width, portal.height);

    // Gambar Player
    ctx.fillStyle = player.warna;
    ctx.fillRect(player.x, player.y, player.width, player.height);
    
    // Gambar Musuh
    ctx.fillStyle = musuh.warna;
    ctx.fillRect(musuh.x, musuh.y, musuh.width, musuh.height);

    // Gambar Serangan Musuh (jika sedang serang)
    if (musuh.sedangSerang) {
        ctx.fillStyle = 'rgba(255, 255, 0, 0.5)'; // Kuning transparan
        ctx.fillRect(musuh.attackBox.x, musuh.attackBox.y, musuh.attackBox.width, musuh.attackBox.height);
    }
}

// --- FUNGSI UTILITAS KOLLISI (AABB) ---
function tabrakan(rect1, rect2) {
    return (
        rect1.x < rect2.x + rect2.width &&
        rect1.x + rect1.width > rect2.x &&
        rect1.y < rect2.y + rect2.height &&
        rect1.y + rect1.height > rect2.y
    );
}


// --- EVENT LISTENERS UNTUK INPUT ---
window.addEventListener('keydown', (event) => {
    switch(event.key) {
        case 'd': // Kanan
        case 'ArrowRight':
            keys.d.pressed = true;
            break;
        case 'a': // Kiri
        case 'ArrowLeft':
            keys.a.pressed = true;
            break;
        case 'w': // Lompat
        case 'ArrowUp':
        case ' ': // Spasi
            // Logika Double Jump
            if (player.lompatanTersisa > 0) {
                player.velocityY = -player.kekuatanLompat;
                player.lompatanTersisa--;
                player.diLantai = false;
            }
            break;
        case 'k': // Dash (ganti ke 'Shift' jika mau)
        case 'Shift':
            // Cek jika bisa dash (tidak sedang cooldown)
            if (player.dashCooldownTimer <= 0 && !player.sedangDash) {
                player.sedangDash = true;
                player.dashTimer = player.dashDurasi;
                player.dashCooldownTimer = player.dashCooldown; // Mulai cooldown
                // Arah dash berdasarkan tombol gerakan yg ditekan
                if (keys.a.pressed) player.velocityX = -1;
                else if (keys.d.pressed) player.velocityX = 1;
                else player.velocityX = (player.x < musuh.x) ? 1 : -1; // Dash ke arah musuh jika diam
            }
            break;
        case 'j': // Parry
            // Cek jika bisa parry
            if (player.parryCooldownTimer <= 0 && !player.sedangParry && player.diLantai) {
                player.sedangParry = true;
                player.parryTimer = player.parryDurasi; // Jendela parry aktif
                player.parryCooldownTimer = player.parryCooldown;
            }
            break;
    }
});

window.addEventListener('keyup', (event) => {
    switch(event.key) {
        case 'd': // Kanan
        case 'ArrowRight':
            keys.d.pressed = false;
            break;
        case 'a': // Kiri
        case 'ArrowLeft':
            keys.a.pressed = false;
            break;
    }
});


// --- MULAI GAME ---
console.log("Game Dimulai. Kontrol: W/A/D = Gerak/Lompat, J = Parry, K/Shift = Dash");
requestAnimationFrame(gameLoop);
