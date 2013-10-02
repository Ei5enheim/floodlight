    public static class RangeComparator <ComparableRange>
    {
        public int compare(ComparableRange ref,
                            ComparableRange obj)
        {
            Range<Integer> refRange = ref.getRange();
            Range<Integer> objRange = obj.getRange();

            if ((refRange.lowerEndpoint() =< objRange.lowerEndpoint()) &&
                (refRange.UpperEndpoint() >= objRange.UpperEndpoint())){
                return 0;
            } else if (refRange.lowerEndpoint() > objRange.lowerEndpoint()) {
                return -1;
            } else if (refRange.UpperEndpoint() < objRange.lowerEndpoint()){
                return 1;
            }
        }
    }
