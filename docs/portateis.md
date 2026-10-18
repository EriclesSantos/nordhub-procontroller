# Usar em portáteis

## Portáteis com Windows

Portáteis x64 com Windows 10/11, como modelos compatíveis das linhas ROG Ally e Legion Go, usam o mesmo auxiliar e os mesmos requisitos de desktops/notebooks. Siga o [guia para computadores](computadores.md). A compatibilidade depende da arquitetura, do Windows, dos drivers e do jogo; o nome da linha do aparelho não substitui essa verificação.

Use Bluetooth ou Wi-Fi local. Se o aparelho já tiver controles integrados, selecione o Xbox virtual Nördhub no jogador desejado nas configurações do jogo/emulador.

## Nintendo Switch / Switch Lite / OLED

O caminho disponível é **Bluetooth experimental**, em um console que já tenha Atmosphère e MissionControl compatíveis. Um Switch com sistema original, sem essa configuração, não é um destino suportado pelo app. O modelo do console não garante compatibilidade nem capacidade de instalar Atmosphère.

### Programas necessários

- Nördhub ProController no celular Android 9+, com suporte HID Device do fabricante.
- [Atmosphère](https://github.com/Atmosphere-NX/Atmosphere/releases) já configurado no console.
- [MissionControl — instalação e requisitos](https://github.com/ndeadly/MissionControl#installation) e [releases oficiais](https://github.com/ndeadly/MissionControl/releases), compatíveis com o firmware/Atmosphère instalados.

O auxiliar Windows e o ViGEmBus não são necessários para conectar o celular diretamente ao Switch. Antes de atualizar arquivos do cartão, preserve um backup e siga as instruções da versão do MissionControl escolhida; reinicie o console após a instalação.

### Parear

1. No Android, escolha **Switch experimental**, conexão **Padrão**, e toque em **Parear novo**.
2. Autorize Bluetooth, Dispositivos próximos e visibilidade quando solicitado, voltando para a tela do app.
3. Aguarde o app informar que o HID foi registrado e está aguardando o Switch.
4. No console, abra **Controles → Mudar ordem/modo de segurar**.
5. Confirme o pedido de pareamento no Android, se aparecer, e aguarde o canal HID.
6. Pressione **L + R** no controle ou use **Mais → Confirmar L+R** para registrar o jogador.
7. Selecione a ordem/jogador desejado no Switch. O layout visual Nintendo, Xbox ou PlayStation pode ser escolhido independentemente desse destino Bluetooth.

Para sessões seguintes, mantenha o vínculo e use **Reconectar** no app. Ao terminar, use **Encerrar conexão/Desconectar**. Não é necessário apagar todos os controles do console.

O caminho Nintendo atual envia botões, direcional e analógicos. Movimento IMU Nintendo, rumble nativo, NFC e áudio de controle não estão implementados. Gatilhos são digitais. Wi-Fi para o Switch não está disponível nesta versão. Os [ajustes de mira](recursos.md) usam o analógico direito, sem se apresentar como IMU Nintendo.

## Portáteis Android como controle

Um aparelho Android 9+ pode executar o APK como superfície de controle. Para Bluetooth, o fabricante precisa disponibilizar HID Device; para mira por movimento, o aparelho precisa ter giroscópio e acelerômetro. Wi-Fi local com o auxiliar Windows é a alternativa ao Bluetooth para PC; a câmera opcional permite conectar lendo o QR, e os dados manuais ficam em Avançado. Instalar o APK num portátil Android não transforma esse aparelho em receptor de comandos do celular.

## Steam Deck e SteamOS

O auxiliar desta versão é Windows x64. Não há auxiliar Linux/SteamOS ou macOS publicado. Um portátil com Windows x64 usa o procedimento Windows; não há garantia de funcionamento do auxiliar por Proton/Wine.

## Consoles físicos Xbox e PlayStation

Xbox One/Series, PS4 e PS5 não oferecem conexão direta suportada por este APK. Os desenhos Xbox e PlayStation são layouts dos botões. Não é necessário comprar adaptadores para usar os destinos Windows/Switch descritos acima; suporte a outros consoles não acompanha esta versão.
