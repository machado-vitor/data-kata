# Roteiro de Apresentacao

---

### 1. Diagrama — visao geral (30 seg)

Abre o README, mostra o diagrama completo.

> "Esse e o fluxo todo. Tres fontes diferentes chegando num pipeline unificado ate uma API de resultados. Vou percorrer da esquerda pra direita mostrando cada peca rodando."

---

### 2. Concreto — dados brutos nas fontes (1.5 min)

Conecta no Postgres e mostra os dados que estao na origem:

```sql
SELECT salesman_name, city, amount, product FROM sales LIMIT 10;
```

Abre o MinIO em `localhost:9001`, navega ate um dos arquivos CSV de seed.

> "Tres fontes completamente diferentes em estrutura e protocolo. O desafio comeca aqui."

---

### 3. Diagrama — Kafka e Flink (30 seg)

Aponta os producers, o Kafka e os tres jobs do Flink no diagrama.

> "Cada producer publica no seu topico. O Flink normaliza tudo num schema unico e depois agrega em janelas de 1 hora."

---

### 4. Concreto — Flink UI ao vivo (1.5 min)

Abre `localhost:8081`.

Mostra os 3 jobs rodando:
- `NormalizationJob` — consume os 3 topicos, publica `sales.unified`
- `TopSalesCityJob` — janela de 1h, top 10 cidades
- `TopSalesmanCountryJob` — janela de 1h, top 10 vendedores no Brasil

Aponta o throughput (records/s) e o checkpoint funcionando.

> "O NormalizationJob e o que transforma tres schemas diferentes em um so. So depois disso os jobs de agregacao entram."

---

### 5. Diagrama — ClickHouse (30 seg)

Aponta o ClickHouse no diagrama.

> "Quando o Flink fecha uma janela, ele faz um batch insert direto no ClickHouse. Vou mostrar os dados chegando."

---

### 6. Concreto — dados persistindo no ClickHouse (1.5 min)

Roda a query e executa duas vezes com alguns segundos de intervalo para mostrar os numeros mudando:

```sql
SELECT city, total_amount, window_start
FROM top_sales_city
ORDER BY total_amount DESC
LIMIT 10;
```

> "ReplacingMergeTree garante que se chegar um update tardio da mesma janela, o ClickHouse guarda so o mais recente. Sem logica custom."

---

### 7. Diagrama + Concreto — API (1 min)

Aponta a API no diagrama.

> "Uma camada fina em cima do ClickHouse. So query e resposta."

```bash
curl "http://localhost:8080/api/v1/sales/top-by-city?window=latest&limit=10"
curl "http://localhost:8080/api/v1/sales/top-salesman?window=latest&country=BR&limit=10"
```

Mostra o JSON retornado.

---

### 8. Concreto + Fechamento — Observabilidade e tradeoffs (1 min)

Abre `localhost:3000` (admin/admin), dashboard "Pipeline Health":
- Kafka consumer lag
- Throughput do Flink
- Insert rate no ClickHouse

Abre `localhost:3001` (Marquez), mostra o grafo de lineage.

> "Metricas e lineage incluso. Da pra ver de onde cada dado veio e qual job processou."

> "Algumas decisoes: Java + Flink pela latencia baixa e checkpoint nativo, ClickHouse pelo analitico colunar, KRaft pra remover o Zookeeper. Cada escolha tem tradeoff documentado no ARCHITECTURE.md."

---

**Antes de comecar:**

```bash
make up      # sobe tudo
make status  # confirma que tudo esta de pe
```
