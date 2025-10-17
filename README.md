# Calculadora de Combustível

Aplicativo Android desenvolvido em Kotlin com Jetpack Compose para ajudar motoristas a decidir entre álcool e gasolina com base no preço praticado em cada posto.

## Visão Geral
- O cálculo compara os preços informados e aplica automaticamente a regra dos 70% ou 75%, de acordo com o seletor na interface.
- O valor do seletor (70% ↔ 75%) e os campos preenchidos são preservados com `rememberSaveable`, garantindo que a escolha resista a rotações e recriações de atividade.
- Interface construída com Material 3, paleta customizada para modo claro/escuro e layout refinado (spacing, cards e controles responsivos).
- Ícone próprio distribuído em todos os diretórios `mipmap`, diferenciando o app da versão de referência do professor.

## Funcionalidades
- **Cálculo dinâmico**: converte valores com formatação BR (`NumberFormat`) e decide automaticamente o melhor combustível.
- **Seletor de limiar**: `Switch` alterna entre 70% e 75%; ao mudar, o cálculo utiliza o novo percentual instantaneamente.
- **Feedback contextualizado**: mensagem final traz emoji, combustível recomendado e, se informado, o nome do posto.
- **Persistência de estado**: inputs e seleção sobrevivem a reconfigurações graças ao uso de `rememberSaveable`.
- **Tema refinado**: novas cores para claro/escuro (`PrimaryLight/Dark`, `SecondaryLight/Dark`, etc.) e componentes estilizados.

## Pré-visualizações
![Abertura](previews/Preview_APP_Opening.png)
![Tela principal](previews/Preview_APP_Home.png)
![Resultado do cálculo](previews/Preview_APP_Result.png)

## Stack e Ferramentas
- Kotlin 2.0.21
- Jetpack Compose (Material 3 + BOM 2024.09.00)
- Android Gradle Plugin 8.13.0
- Minimum SDK 24 | Target SDK 36

## Como Executar
1. Requisitos: Android Studio Jellyfish | Koala ou superior, SDK 24+, JDK 11.
2. Clone ou faça download do projeto.
3. Abra o diretório na IDE e aguarde a sincronização do Gradle.
4. Execute em um dispositivo/emulador com `Run > Run 'app'` ou use a linha de comando:

```bash
./gradlew assembleDebug
```

O APK gerado estará em `app/build/outputs/apk/debug/`.

## Estrutura
```
CalculadoradeCombustivel/
├── app/src/main/java/org/pdm/calculadoradecombustivel/MainActivity.kt
├── app/src/main/java/org/pdm/calculadoradecombustivel/ui/theme/{Color,Theme,Type}.kt
├── app/src/main/res/mipmap-*/ic_launcher*.webp
└── previews/Preview_APP_*.png
```

> Este projeto foi desenvolvido durante a cadeira de Programação para Dispositivos Móveis (2025.2) do curso de SMD da UFC.