"""Testes do gerador de composição da CVM: endereços reais lidos com segurança, ligação ticker-CNPJ só quando tudo concorda.

Rodar: python -m unittest scripts/curadoria/test_gerar_composicao_cvm.py
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gerar_composicao_cvm as g  # noqa: E402


class EnderecoTest(unittest.TestCase):
    def test_formatos_vistos_nos_informes(self):
        casos = {
            "Praça Bom Jesus, 154, Palmas, PR": ("Palmas", "PR", None),
            "Rodovia ES-010, n 3.917 - Jardim Limoeiro - Serra-ES, CEP 29163-370": ("Serra", "ES", "29163-370"),
            "Av. Prof. Carlos Cunha, 1000 - Jaracaty, São Luís - MA": ("São Luís", "MA", None),
            "Av. Brigadeiro Faria Lima nº 1.461,  Pinheiros, São Paulo/SP": ("São Paulo", "SP", None),
            "Rua Do Passeio, 38, Sem Complemento, Centro, Rio De Janeiro/RJ, Brasil, 20021-290": ("Rio De Janeiro", "RJ", "20021-290"),
            "Rua X, 333 - Cachoeirinha - RS": ("Cachoeirinha", "RS", None),
        }
        for endereco, esperado in casos.items():
            self.assertEqual(g.local_cvm(endereco), esperado, endereco)

    def test_sem_cidade_ou_uf_nada_e_adivinhado(self):
        self.assertEqual(g.local_cvm("Estrada M. Luiz Lopes Neto"), (None, None, None))
        self.assertEqual(g.local_cvm(None), (None, None, None))
        # sigla que não é UF não vira estado
        self.assertEqual(g.local_cvm("Rua A, 10 - Centro - XX")[1], None)


class NumerosTest(unittest.TestCase):
    def test_fracao_da_cvm_vira_percentual(self):
        self.assertEqual(g.pct("0.103933"), "10.3933")
        self.assertEqual(g.pct("1"), "100")
        self.assertIsNone(g.pct(""))
        self.assertIsNone(g.pct("-"))
        self.assertIsNone(g.pct("abc"))


class TickerTest(unittest.TestCase):
    FIIS = {"XPLG11": "XP LOG FUNDO DE INVESTIMENTO IMOBILIÁRIO", "ABCD11": "ABCD FUNDO", "ABCD12": "ABCD FUNDO II"}

    def test_isin_nome_e_catalogo_concordando_ligam_o_ticker(self):
        mapa, desc = g.mapear_tickers({"1": "BRXPLGCTF002"}, {"1": "XP LOG FII RL"}, self.FIIS)
        self.assertEqual(mapa, {"1": "XPLG11"})
        self.assertEqual(desc, [])

    def test_nome_que_nao_concorda_deixa_o_fundo_de_fora(self):
        mapa, desc = g.mapear_tickers({"1": "BRXPLGCTF002"}, {"1": "OUTRO NOME QUALQUER"}, self.FIIS)
        self.assertEqual(mapa, {})
        self.assertEqual(desc[0][1], "XPLG11")

    def test_dois_cnpjs_para_o_mesmo_ticker_nenhum_vale(self):
        mapa, desc = g.mapear_tickers({"1": "BRXPLGCTF002", "2": "BRXPLGCTF003"}, {"1": "XP LOG FII", "2": "XP LOG FII II"}, self.FIIS)
        self.assertEqual(mapa, {})
        self.assertEqual(len(desc), 2)

    def test_isin_fora_do_padrao_ou_ticker_fora_do_catalogo_e_ignorado(self):
        mapa, _ = g.mapear_tickers({"1": "BRFOOBARXXX001", "2": "BRZZZZCTF001"}, {"1": "XP LOG", "2": "XP LOG"}, self.FIIS)
        self.assertEqual(mapa, {})


if __name__ == "__main__":
    unittest.main()
