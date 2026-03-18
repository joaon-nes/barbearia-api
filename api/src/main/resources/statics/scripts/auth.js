const API_URL = 'http://localhost:8080/api/usuarios';

const formCadastro = document.getElementById('formCadastro');
if (formCadastro) {
    formCadastro.addEventListener('submit', async (e) => {
        e.preventDefault();

        const novoUsuario = {
            nome: document.getElementById('nomeCadastro').value,
            email: document.getElementById('emailCadastro').value,
            telefone: document.getElementById('telefoneCadastro').value,
            senha: document.getElementById('senhaCadastro').value,
            role: document.querySelector('input[name="tipoConta"]:checked').value
        };

        try {
            const response = await fetch(`${API_URL}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(novoUsuario)
            });

            if (response.ok) {
                alert('Conta criada com sucesso! Verifique o seu e-mail para ativar a conta antes de iniciar sessão.');
                window.location.href = 'login.html';
            } else {
                const erro = await response.text();
                alert(erro);
            }
        } catch (error) {
            console.error("Erro:", error);
            alert("Erro ao conectar com o servidor.");
        }
    });
}

let emailAguardando2FA = "";

const formLogin = document.getElementById('formLogin');
if (formLogin) {
    formLogin.addEventListener('submit', async (e) => {
        e.preventDefault();

        const credenciais = {
            email: document.getElementById('emailLogin').value,
            senha: document.getElementById('senhaLogin').value
        };

        try {
            const response = await fetch(`${API_URL}/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(credenciais)
            });

            if (response.status === 202) {
                const data = await response.json();
                emailAguardando2FA = data.email;

                document.getElementById('formLogin').querySelector('button[type="submit"]').style.display = 'none';
                document.getElementById('area2FA').style.display = 'block';

            } else if (response.ok) {
                const usuarioData = await response.json();
                finalizarLogin(usuarioData);

            } else {
                const erro = await response.text();
                alert(erro);
            }
        } catch (error) {
            alert("Erro ao conectar com o servidor.");
        }
    });
}

const form2FA = document.getElementById('form2FA');
if (form2FA) {
    form2FA.addEventListener('submit', async (e) => {
        e.preventDefault();

        const payload = {
            email: emailAguardando2FA,
            codigo: document.getElementById('codigo2faInput').value
        };

        try {
            const response = await fetch(`${API_URL}/validar-2fa`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (response.ok || response.status === 206) {
                const data = await response.json();

                if (response.status === 206) {
                    localStorage.setItem('usuarioLogado', JSON.stringify(data.usuario));
                    if (data.token) localStorage.setItem('token', data.token);

                    window.location.href = 'completar-perfil.html';
                } else {
                    finalizarLogin(data);
                }
            } else {
                const erro = await response.text();
                alert(erro);
            }
        } catch (error) {
            alert("Erro ao validar o código 2FA.");
        }
    });
}

function finalizarLogin(data) {
    const usuario = data.usuario;
    const token = data.token;

    localStorage.setItem('usuarioLogado', JSON.stringify(usuario));
    if (token) {
        localStorage.setItem('token', token);
    }

    if (usuario.role === 'ESTABELECIMENTO' && !usuario.perfilCompleto) {
        window.location.href = 'completar-perfil.html';
        return;
    }

    alert(`Bem-vindo, ${usuario.nome}!`);

    if (usuario.role === 'ADMIN') {
        window.location.href = 'dashboard-admin.html';
    } else if (usuario.role === 'ESTABELECIMENTO') {
        window.location.href = 'estabelecimento.html';
    } else {
        window.location.href = 'cliente.html';
    }
}