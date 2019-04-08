package com.khartec.waltz.service.allocation;

import com.khartec.waltz.common.Checks;
import com.khartec.waltz.common.CollectionUtilities;
import com.khartec.waltz.common.SetUtilities;
import com.khartec.waltz.model.allocation.Allocation;
import com.khartec.waltz.model.allocation.AllocationType;
import com.khartec.waltz.model.allocation.MeasurablePercentage;
import com.sun.tools.classfile.Opcode;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;

import java.math.BigDecimal;
import java.util.List;

import java.util.Set;
import java.util.stream.Collectors;

import static com.khartec.waltz.common.Checks.checkTrue;
import static com.khartec.waltz.common.CollectionUtilities.any;
import static com.khartec.waltz.common.CollectionUtilities.countWhere;
import static com.khartec.waltz.model.allocation.MeasurablePercentage.mkMeasurablePercentage;
import static java.util.stream.Collectors.*;
import static org.jooq.lambda.tuple.Tuple.tuple;

public class AllocationUtilities {

    private static final BigDecimal ONE_HUNDRED_PERCENT = BigDecimal.valueOf(100);


    public static Tuple2<AllocationType, MeasurablePercentage> fromAllocation(Allocation allocation) {
        return tuple(
                allocation.type(),
                mkMeasurablePercentage(allocation.measurableId(), allocation.percentage()));
    }


    public static boolean validateAllocationTuples(List<Tuple2<AllocationType, BigDecimal>> tuples) {

        if (anyNegatives(tuples)) {
            return false;
        }

        BigDecimal fixedTotal = calculateFixedTotal(tuples);

        if (hasFloats(tuples)) {
            return fixedTotal.compareTo(ONE_HUNDRED_PERCENT) <= 0;
        } else {
            return fixedTotal.equals(ONE_HUNDRED_PERCENT);
        }
    }


    public static BigDecimal calculateFloatingPercentage(List<Tuple2<AllocationType, BigDecimal>> allocations) {
        checkTrue(validateAllocationTuples(allocations), "Allocations failed validation");

        long numFloats = countFloats(allocations);

        BigDecimal floatingPoolPercentage = ONE_HUNDRED_PERCENT.subtract(calculateFixedTotal(allocations));
        BigDecimal floatPercentage;

        if (floatingPoolPercentage == BigDecimal.ZERO){
            floatPercentage = floatingPoolPercentage;
        } else {
            floatPercentage = floatingPoolPercentage.divide(BigDecimal.valueOf(numFloats));
        }

        return floatPercentage;
    }


    private static BigDecimal calculateFixedTotal(List<Tuple2<AllocationType, BigDecimal>> allocations){
        return allocations
                .stream()
                .filter(t -> t.v1 == AllocationType.FIXED)
                .map(t -> t.v2)
                .collect(reducing(BigDecimal.ZERO, (acc, d) -> acc.add(d)));
    }



    public static <T> List<Tuple3<AllocationType, BigDecimal, T>> toFloat(List<Tuple3<AllocationType, BigDecimal, T>> allocations, T itemToChange){
        // check for nulls / errors
        // check itemTOChange exists in list
        // check that item to change is actually changing, if not just return allocations

        // item is changing
        // ---
        // find the sum of fixed allocations without itemToChange in them (sfx)

        List<Tuple3<AllocationType, BigDecimal, T>> allFixedUnchanged = allocations
                .stream()
                .filter(t -> !t.v3.equals(itemToChange) && t.v1.equals(AllocationType.FIXED))
                .collect(toList());

        BigDecimal newSumOfFixed = allFixedUnchanged
                .stream()
                .map(t -> t.v2)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // new floating sum (sfl) is 100 - sfx
        BigDecimal newSumOfFloating = BigDecimal.valueOf(100).subtract(newSumOfFixed);

        // work out a count of floating items + itemToChange (countFl)
        long newCountOfFloating = allocations
                .stream()
                .filter(t -> t.v1.equals(AllocationType.FLOATING))
                .count() + 1;

        // redistribute floating allocations as sfl / countFl
        BigDecimal newAllocationOfFloating = newSumOfFloating.divide(BigDecimal.valueOf(newCountOfFloating));

        // update itemToChange to be floating and change allocation
        Tuple3<AllocationType, BigDecimal, T> itemToChangeTuple = allocations
                .stream()
                .filter(t -> t.v3.equals(itemToChange))
                .findFirst()
                .get();

        Tuple3<AllocationType, BigDecimal, T> remappedItemToChangeTuple = itemToChangeTuple
                .map1(at -> AllocationType.FLOATING)
                .map2(d -> newAllocationOfFloating);


        // reallocate floating percentages
        List<Tuple3<AllocationType, BigDecimal, T>> floatingReallocated = allocations
                .stream()
                .filter(t -> t.v1.equals(AllocationType.FLOATING))
                .map(t -> t.map2(d -> newAllocationOfFloating))
                .collect(toList());

        //  rebuilt list
        allFixedUnchanged.addAll(floatingReallocated);
        allFixedUnchanged.add(remappedItemToChangeTuple);
        return allFixedUnchanged;
    }


    private static boolean anyNegatives(List<Tuple2<AllocationType, BigDecimal>> tuples) {
        return any(tuples, t -> t.v2.signum() == -1);
    }


    private static boolean hasFloats(List<Tuple2<AllocationType, BigDecimal>> tuples) {
        return any(tuples, t -> t.v1 == AllocationType.FLOATING);
    }


    private static long countFloats(List<Tuple2<AllocationType, BigDecimal>> allocations) {
        return countWhere(allocations, t -> t.v1 == AllocationType.FLOATING);
    }



}