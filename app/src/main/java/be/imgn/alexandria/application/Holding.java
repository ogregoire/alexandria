package be.imgn.alexandria.application;

import be.imgn.alexandria.domain.catalog.Catalog;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.Work;

import java.util.ArrayList;
import java.util.List;

/**
 * One copy, fully placed: the item, the edition it is, the expression that edition embodies,
 * and the work behind it.
 *
 * <p>This is the descent every report used to spell out as a four-table join. Doing it once
 * here is what let the SQL projection go: the joining was the only thing a database was
 * really being asked for.
 *
 * <p>A copy of an omnibus yields one holding per expression it embodies, exactly as the join
 * produced one row per expression.
 */
public record Holding(Item item, Manifestation manifestation, Expression expression, Work work) {

    public static List<Holding> of(Catalog catalog) {
        List<Holding> holdings = new ArrayList<>();
        for (Item item : catalog.items()) {
            catalog.manifestation(item.embodiedIn()).ifPresent(manifestation -> {
                for (var reference : manifestation.embodies()) {
                    catalog.work(reference.work())
                            .flatMap(work -> work.expression(reference)
                                    .map(expression -> new Holding(item, manifestation, expression, work)))
                            .ifPresent(holdings::add);
                }
            });
        }
        return List.copyOf(holdings);
    }

    /** Every edition placed under its work, whether or not a copy is held. */
    public record Edition(Manifestation manifestation, Expression expression, Work work) {

        public static List<Edition> of(Catalog catalog) {
            List<Edition> editions = new ArrayList<>();
            for (Manifestation manifestation : catalog.manifestations()) {
                for (var reference : manifestation.embodies()) {
                    catalog.work(reference.work())
                            .flatMap(work -> work.expression(reference)
                                    .map(expression -> new Edition(manifestation, expression, work)))
                            .ifPresent(editions::add);
                }
            }
            return List.copyOf(editions);
        }
    }
}
