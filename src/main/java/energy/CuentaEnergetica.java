package energy;

public class CuentaEnergetica {
    private double balanceKWh;
    private double totalSolar;
    private double totalEolica;
    private double totalBasica;

    public CuentaEnergetica(double balanceInicialKWh) {
        this.balanceKWh = balanceInicialKWh;
    }

    synchronized public void anotaConsumo(double kWh) {
        this.balanceKWh += kWh;
    }

    public double getBalanceKWh() {
        return balanceKWh;
    }

    synchronized void anotaConsumo(double totalBasica, double solar, double eolica) {
        this.totalBasica += totalBasica;
        this.totalSolar += solar;
        this.totalEolica += eolica;
        this.balanceKWh += (totalBasica + totalSolar + totalEolica);

    }

    @Override
    public String toString() {
        return "CuentaEnergetica{balanceKWh=" + balanceKWh + "}";
    }
}
