# Nördhub ProController — auxiliar Windows 0.3.1

Recebe o controle Android por Bluetooth ou Wi-Fi local e disponibiliza um Xbox virtual/XInput para jogos Windows. Extraia o ZIP inteiro e mantenha o EXE, `ViGEmClient.dll` e `zxing.dll` juntos.

**Requisitos:** Windows 10/11 x64, .NET Framework 4.8 e ViGEmBus compatível. Bluetooth exige HID Device no Android; Wi-Fi exige a mesma rede local. ViGEmBus foi descontinuado pelo autor e não é instalado automaticamente.

[Tutorial completo, programas necessários e links oficiais](https://github.com/EriclesSantos/nordhub-procontroller/blob/main/docs/computadores.md) · [Recursos e configurações](https://github.com/EriclesSantos/nordhub-procontroller/blob/main/docs/recursos.md) · [Portáteis](https://github.com/EriclesSantos/nordhub-procontroller/blob/main/docs/portateis.md).

No Wi-Fi, clique em **Wi-Fi / Wireless**. No celular, use **PC → Wi-Fi → Ler QR Code do computador** e aponte a câmera para a janela do PC. Aguarde a confirmação e abra o jogo. O PC e o celular precisam estar na mesma rede; não é necessário digitar IP ou código.

**Avançado** reúne a conexão manual, seleção de gamepad Bluetooth, touchpad como mouse, movimento DSU e diagnóstico. Touchpad/DSU iniciam desabilitados. Fechar a janela mantém o auxiliar na bandeja; Desconectar remove o Xbox virtual e Sair fecha o programa. Use **Sair** no auxiliar antigo antes de abrir uma nova versão.

`GUIA-COMPUTADORES.md` e `recursos.md` acompanham o ZIP para consulta offline. O leitor e o gerador de QR funcionam localmente, sem envio de imagens. A câmera só é usada no celular durante a leitura; conexão manual continua disponível.

O auxiliar não oferece suporte direto a consoles físicos Xbox/PS4/PS5. O driver é externo e o EXE não tem assinatura de distribuição Windows. A licença do cliente nativo acompanha o pacote em `LICENSE.ViGEmClient.txt`.
