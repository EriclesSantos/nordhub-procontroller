# Recursos e ajustes

Os recursos adicionais ficam em **Mais → Perfis e recursos avançados** no Bluetooth ou **Mais → Ajustes e perfis** no Wi-Fi. Os três layouts mantêm os botões básicos, sem acrescentar botões de jogo à superfície.

## Disponibilidade por conexão

| Recurso | Bluetooth PC | Wi-Fi PC | Switch Bluetooth |
|---|---|---|---|
| Botões, direcional e analógicos | Sim | Sim | Sim, experimental/MissionControl |
| Perfis e remapeamento | Sim | Sim | Sim |
| Curvas, sensibilidade, zona morta e inversões | Sim | Sim | Sim |
| Vibração local ao tocar | Sim | Sim | Sim |
| Giroscópio como mira no analógico direito | Sim | Sim | Sim, como analógico direito |
| Gatilhos graduais recebidos pelo jogo | Digital nesta conexão | Sim, 0–255 | Digital nesta conexão |
| Vibração devolvida pelo jogo | Não disponível | Sim, aproximação pelo vibrador Android | Não disponível |
| Touchpad como mouse | Não disponível | Sim, com habilitação no PC | Não disponível |
| Movimento bruto DSU para emulador | Não disponível | Sim, opcional no PC | IMU Nintendo não disponível |

Esta versão ainda não tem paridade completa entre os dois transportes. Recursos de sensores, rede e vibração são experimentais e dependem do hardware e do consumidor. Não há resistência física de gatilhos, áudio/microfone de controle ou NFC.

## Perfis e botões

Em Ajustes, use **Salvar perfil** para guardar layout, mapeamento e ajustes. Use **Carregar perfil**, ou **Mais → Trocar perfil**, para aplicar manualmente um perfil salvo. O app não identifica automaticamente o jogo aberto.

**Remapear botões** escolhe qual comando cada posição envia; também permite desativar uma posição. **Restaurar mapeamento padrão** recupera a distribuição original.

## Analógicos

Ajuste zona morta e sensibilidade separadamente para esquerda/direita. Curva **Linear** oferece resposta proporcional; **Precisão no centro** facilita pequenos movimentos; **Resposta rápida** aumenta a resposta perto do centro. X e Y podem ser invertidos. Não é acrescentada suavização temporal deliberada aos toques.

## Gatilhos

Com graduação habilitada, toque o gatilho e arraste para baixo para aumentar a intensidade, acompanhando o preenchimento do próprio botão. Soltar zera. Desative a graduação para acionamento total imediato. A intensidade gradual chega ao jogo no caminho Wi-Fi; Bluetooth/Nintendo mantêm acionamento digital.

## Giroscópio

1. Escolha **Mira sempre ativa**, **Mira segurando L2/ZL** ou **Somente movimento nativo (DSU no PC)** nos ajustes.
2. Ajuste sensibilidade e inversões da mira conforme a posição do telefone.
3. Em **Mais → Calibrar giroscópio**, mantenha o telefone imóvel por cerca de um segundo. Movimento reinicia a coleta.

A mira soma o movimento ao analógico direito; isso permite usá-la onde há suporte ao analógico, sem exigir movimento nativo no jogo. O modo DSU usa um canal específico para emuladores. Sensores são liberados ao sair da superfície de controle.

## Movimento DSU no emulador

Use Wi-Fi, abra **Avançado** no auxiliar e habilite **Movimento para emuladores (DSU)** e ative um modo de giroscópio no celular. No emulador compatível, configure o servidor **127.0.0.1:26760**, slot/jogador 0. Os botões continuam no Xbox virtual; o movimento é separado. Se quiser somente movimento bruto, escolha o modo DSU para evitar sobrepor a mira no analógico direito.

O servidor só escuta no próprio PC. Se a porta estiver ocupada, o auxiliar informa indisponibilidade do DSU e mantém o Xbox. A implementação segue os [campos públicos DSU/Cemuhook](https://github.com/v1993/cemuhook-protocol). O emulador precisa implementar esse protocolo.

## Vibração

**Vibração ao tocar** regula o feedback local. **Vibração recebida do jogo (Wi-Fi)** regula o retorno dos motores XInput ao telefone. A reprodução é uma aproximação pelo vibrador Android; não equivale ao conjunto de atuadores de um controle físico.

## Touchpad

No Wi-Fi, abra **Avançado → Permitir touchpad como mouse** no auxiliar e escolha **Mais → Touchpad** no celular. A superfície muda para touchpad; **Mais → Voltar aos botões** retorna ao controle.

- Um dedo move o cursor; toque faz clique esquerdo.
- Segure e mova para arrastar.
- Dois dedos rolam; toque com dois dedos faz clique direito.

O mouse inicia desabilitado no auxiliar. O Windows pode bloquear entrada entre processos com privilégios diferentes. Esse recurso não configura mapeamento universal de teclado/mouse para jogos sem gamepad.

## Conexão com tela bloqueada

Um serviço em primeiro plano mantém a sessão separada da Activity. Ao sair/bloquear, o app libera botões, mouse e sensores para evitar comandos presos; ao voltar, reutiliza a sessão existente. **Desconectar** encerra a conexão. Forçar parada, desligar Bluetooth/rede ou encerramento pelo Android/fabricante podem interrompê-la. O comportamento e o consumo variam conforme as políticas de bateria do aparelho.
