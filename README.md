# Smart Driver – Calculadora de ofertas para motorista de app

Lê a oferta que aparece no Uber Driver, calcula se compensa e mostra **ACEITAR** ou **RECUSAR**
numa janela flutuante pequena, por cima do Uber.
**Quem decide e toca no Uber é sempre você.** O app não aceita nem recusa nada sozinho.

> Projeto independente. Não é afiliado, patrocinado nem aprovado pela Uber.

## ⬇️ Baixar

**[Baixar SmartDriver.apk](https://github.com/EriclesAlves13/smart-driver/releases/latest/download/SmartDriver.apk)**
(Android 7.0 ou mais novo · cerca de 19 MB)

Todas as versões ficam em [Releases](https://github.com/EriclesAlves13/smart-driver/releases).

---

## Como instalar
1. Abra o link acima **no celular** e baixe o `SmartDriver.apk`.
2. Toque no arquivo baixado. Se o Android pedir, permita **"instalar apps desconhecidos"** para o navegador.
3. O Play Protect pode avisar que o app não é da Play Store. Toque em **Instalar mesmo assim**.

## Como usar
1. Abra o **Smart Driver** e preencha:
   - **Consumo** do carro em km/L (ex: `11,5`). Obrigatório.
   - **Preço do combustível** em R$/litro (ex: `6,19`). Obrigatório.
   - **Cidade onde você roda** (padrão: Maranguape).
   - *(Opcional)* **Outras cidades liberadas**: toque em **+ Adicionar** (ex: `Maracanaú`).
     Destinos nessas cidades contam como "na cidade".
   - *(Opcional)* **Locais liberados fora da cidade**: toque em **+ Adicionar** (ex: `Shopping Maracanaú`).
     Para remover, toque na etiqueta.
   - *(Opcional)* **Bairros da cidade**: use quando o Uber mostra só rua e bairro.
   - **Regras**: mínimo por km (padrão R$ 2,00) e mínimo por corrida (padrão R$ 8,00).
     Edite os valores direto na tela. Em branco volta ao padrão.
2. Toque em **Permitir janela flutuante** e ative a opção para o Smart Driver.
3. Toque em **Iniciar leitura** e autorize. No Android 14 ou mais novo, escolha
   **"Tela inteira"** ou o app **Uber Driver**. O Uber Driver abre sozinho.
   O selo no topo mostra o estado: *Pronto*, *Lendo a tela*, *Configure o carro* ou *Falta permissão*.
4. Quando chegar uma oferta, a janela mostra:

   | Campo | Exemplo |
   |---|---|
   | Valor | R$ 25,90 |
   | Distância | 10,0 km (1,5 + 8,5) |
   | R$/km | R$ 2,59 |
   | Combustível | R$ 6,00 |
   | Lucro | R$ 19,90 |
   | Destino | na cidade ✓ |
   | **Resultado** | **ACEITAR** |

5. Você toca em aceitar ou recusar **no próprio Uber**.

A janela abre **compacta**: o resultado com o R$/km do lado e uma linha com valor, km, lucro e destino.
Botões da janela: **✎** corrige ou completa dados à mão. **⌄** mostra os detalhes (quadro completo),
e **—** volta para o modo compacto.
Arraste a janela para onde ela não cubra os botões do Uber. Para desligar, toque em **Parar leitura**
no app ou use a notificação → **Parar**.

## Visual
Tema escuro **"Painel Noturno"**, feito para ler rápido dirigindo e sem ofuscar à noite:
- O resultado aparece num bloco grande: 🟢 **ACEITAR**, 🔴 **RECUSAR** ou 🟡 **CONFIRME O DESTINO / DADOS INCOMPLETOS**.
  Cada um tem ícone (✓ ✗ ⚠) e texto, então dá para entender sem depender só da cor.
- O **R$/km fica em destaque**, por ser o número que mais pesa na decisão.
- Valor, distância (busca + viagem), combustível e lucro ficam num quadro 2×2.
- Ao recusar, os **motivos aparecem listados**, por exemplo "R$ 1,30/km — abaixo de R$ 2,00".
- Quando a cidade de destino não aparece na tela, a janela mostra **"É na cidade" / "É fora"**.
  Você resolve com um toque, sem abrir outra tela.
- Na tela de edição, cada campo diz de onde veio: **lido da tela**, **não identificado** ou **digitado ✎**.

## Regras do cálculo
| Regra | Valor |
|---|---|
| Mínimo por km | R$ 2,00 por padrão, editável (valor ÷ km totais) |
| Mínimo por corrida | R$ 8,00 por padrão, editável |
| Destino | somente na cidade escolhida, numa das outras cidades liberadas ou num local liberado |
| Combustível | km totais ÷ consumo × preço do litro |
| Lucro | valor − combustível |

**Km totais = km até o passageiro + km da viagem**, porque o combustível é gasto nos dois trechos.

| Resultado | Quando aparece |
|---|---|
| 🟢 **ACEITAR** | Todas as regras foram atendidas. |
| 🔴 **RECUSAR** | Alguma regra falhou. O motivo aparece embaixo. |
| 🟡 **CONFIRME O DESTINO** | Os valores estão OK, mas a cidade de destino não apareceu na tela. |
| 🟡 **DADOS INCOMPLETOS** | Faltou algum dado. A janela diz qual e você completa em *Editar*. |

### Locais liberados
Para rodar só na sua cidade e ainda aceitar destinos específicos fora dela, como um shopping,
cadastre esses lugares em **Locais liberados**. A ordem das palavras e os acentos não importam:
`Shopping Maracanaú` também reconhece "North Shopping Maracanaú" e "MARACANAU SHOPPING".
As regras de R$/km e valor mínimo continuam valendo.
O local só é reconhecido se o nome aparecer na tela da oferta. Se o Uber mostrar só o endereço,
cadastre também o nome da rua.

### Transparência dos dados
- O que o app não conseguiu ler aparece como **"não identificado"**. Ele nunca inventa um valor.
- O que você digitar aparece com **✎** e a janela avisa
  **"Análise com dados digitados — não é 100% automática"**.

---

## Como foi feito

### Como os dados da oferta são obtidos
| Método | Situação |
|---|---|
| **Captura de tela oficial do Android (MediaProjection) + reconhecimento de texto (OCR) no próprio celular** | ✅ **Usado.** API pública do Android. Você autoriza a cada sessão e o sistema mostra o aviso de captura. O app só lê a imagem da tela. |
| Serviço de Acessibilidade | ❌ Não usado. Permite tocar em outros apps e a Google restringe esse uso. É mais invasivo. |
| API ou dados internos da Uber | ❌ Não usado. Não existe API pública de ofertas para motoristas, e usar dados internos seria engenharia reversa. |
| Aceitar corridas automaticamente | ❌ Não implementado, de propósito. |

### Fluxo
```
Tela do celular ──(MediaProjection, ~1,4 quadros/s)──► imagem reduzida (720 px)
      │                                                       │
      │                         a área da janela flutuante é tampada
      ▼                                                       ▼
 tela preta? ──► avisa "captura bloqueada"           OCR (Google ML Kit, offline)
                                                              │
                                                              ▼
                                               OfferParser: valor, km, minutos, destino
                                                              │
                                                              ▼
                                               Calculator: R$/km, combustível, lucro, regras
                                                              │
                                                              ▼
                                               Janela flutuante: ACEITAR / RECUSAR
```

### Privacidade e desempenho
- O OCR roda **offline, dentro do celular**. O app **não tem permissão de internet**: a telemetria
  do ML Kit foi removida do manifesto, e nada sai do aparelho.
- **Sem histórico.** Só a configuração (consumo, preço, regras, cidades, bairros e locais) fica salva.
- A análise sai em menos de 1 segundo depois da leitura da tela, e o tempo aparece na janela.
- As telas do próprio app pausam a leitura, para não ler o formulário de edição.

### Código
| Arquivo | Função |
|---|---|
| [`OfferParser.java`](app/src/main/java/com/smartdriver/calc/OfferParser.java) | Extrai os dados do texto lido (R$, "4 min (1,5 km)", "Viagem de…", destino) |
| [`Calculator.java`](app/src/main/java/com/smartdriver/calc/Calculator.java) | Aplica as regras e decide o resultado |
| [`CaptureService.java`](app/src/main/java/com/smartdriver/calc/CaptureService.java) | Captura autorizada da tela + OCR |
| [`OverlayController.java`](app/src/main/java/com/smartdriver/calc/OverlayController.java) | Janela flutuante arrastável |
| [`MainActivity.java`](app/src/main/java/com/smartdriver/calc/MainActivity.java) | Configuração e início |
| [`ManualActivity.java`](app/src/main/java/com/smartdriver/calc/ManualActivity.java) | Preenchimento e correção manual |
| [`OfferTest.java`](app/src/test/java/com/smartdriver/calc/OfferTest.java) | 21 testes da leitura e do cálculo |
| [`UiSmokeTest.java`](app/src/test/java/com/smartdriver/calc/UiSmokeTest.java) | 4 testes que abrem as telas e desenham a janela em todos os resultados (Robolectric) |

Java puro, sem bibliotecas além do Google ML Kit Text Recognition. minSdk 24, targetSdk 34.
Fonte dos números e títulos: [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk) (SIL Open Font License).

### Compilar
Requer JDK 17 e Android SDK 34.
```bat
set JAVA_HOME=C:\caminho\para\jdk17
echo sdk.dir=C:/caminho/para/android/sdk> local.properties
gradlew.bat testDebugUnitTest assembleRelease
```
O APK sai em `app/build/outputs/apk/release/app-release.apk`.

## Limitações conhecidas
- **Ainda não foi testado com ofertas reais.** A leitura foi validada por testes automáticos com
  texto no formato do cartão de oferta, e as telas por um simulador de Android (Robolectric). Se o layout do Uber for diferente, o app mostra
  "não identificado" em vez de inventar. Prints de ofertas reais ajudam a ajustar a leitura.
- Se o Uber bloquear a captura de tela, a imagem chega preta. O app avisa e você usa *Editar*.
- Uma distância isolada, sem "viagem" ou "de distância" por perto, não é atribuída a nenhum trecho.
- O APK é assinado com chave de desenvolvimento e não está na Play Store.
- Os termos de uso da Uber podem mudar. O app só lê a tela e não interage com o Uber, mas
  confira os termos do seu contrato.
