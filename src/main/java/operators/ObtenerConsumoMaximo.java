package operators;

import energy.Consumo;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Tarea Callable que busca el consumo con mayor demanda total de kWh.
 */
public class ObtenerConsumoMaximo implements Callable<Consumo> {
    private final List<Consumo> consumos;

    public ObtenerConsumoMaximo(List<Consumo> consumos) {
        this.consumos = consumos;
    }

    @Override
    public Consumo call() throws Exception {
        if (consumos == null || consumos.isEmpty()) {
            return null;
        }
        return consumos.stream()
                .max(Comparator.comparingDouble(Consumo::getTotalKWh))
                .orElse(null);
    }
}
