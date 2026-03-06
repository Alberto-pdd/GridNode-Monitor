package energy;

public class CuentaEnergetica {
    private double balanceKWh;
    private double totalSolar;
    private double totalEolica;
    private double totalRapido;

    public CuentaEnergetica(double balanceInicialKWh) {
        this.balanceKWh = balanceInicialKWh;
    }

    synchronized public void anotaConsumo(double kWh) {
        this.balanceKWh += kWh;
    }

    public double getBalanceKWh() {
        return balanceKWh;
    }

    public synchronized void anotaConsumoDet(double totalRapido, double solar, double eolica) {
        this.totalRapido += totalRapido;
        this.totalSolar += solar;
        this.totalEolica += eolica;
        this.balanceKWh += (totalRapido + totalSolar + totalEolica);

    }

    @Override
    public String toString() {
        return "CuentaEnergetica{balanceKWh=" + balanceKWh + "}";
    }
}
