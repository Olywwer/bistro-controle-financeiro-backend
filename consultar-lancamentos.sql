SELECT
    entry->>'date' AS data_lancamento,
    entry->>'description' AS descricao,
    entry->>'createdByName' AS usuario_lancamento,
    entry->>'createdByUsername' AS login_lancamento,
    entry->>'createdAt' AS data_hora_registro,
    entry->>'type' AS tipo,
    entry->>'category' AS categoria,
    COALESCE(NULLIF(entry->>'amount', '')::numeric, 0) AS valor,
    entry->>'status' AS status,
    entry->>'dueDate' AS data_vencimento,
    entry->>'settledAt' AS data_baixa,
    COALESCE(
        supplier.name,
        bank.name,
        entry->>'bank',
        ''
    ) AS fornecedor_ou_banco
FROM bistro_financial_data AS financial
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(financial.data->'entries', '[]'::jsonb)
) AS entries(entry)
LEFT JOIN LATERAL (
    SELECT supplier_item->>'name' AS name
    FROM jsonb_array_elements(
        COALESCE(financial.data->'suppliers', '[]'::jsonb)
    ) AS suppliers(supplier_item)
    WHERE supplier_item->>'id' = entry->>'supplierId'
    LIMIT 1
) AS supplier ON true
LEFT JOIN LATERAL (
    SELECT bank_item->>'name' AS name
    FROM jsonb_array_elements(
        COALESCE(financial.data->'banks', '[]'::jsonb)
    ) AS banks(bank_item)
    WHERE bank_item->>'id' = entry->>'bankId'
    LIMIT 1
) AS bank ON true
WHERE financial.id = 1
ORDER BY entry->>'date' DESC, entry->>'description';
