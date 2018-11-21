package snob.simulation.snob2.data.Strata;

import java.io.UnsupportedEncodingException;

public class Cell {
    private int count;
    private int idSum;
    private int hashSum;

    public void add(int id, int idHashValue) {
        idSum ^= id;
        hashSum ^= idHashValue;
        count++;
    }

    public void delete(int id, int idHashValue) {
        idSum ^= id;
        hashSum ^= idHashValue;
        count--;
    }

    public boolean isPure() {
        try {
            if ((count == -1 || count == 1)
                    && (IBF.genIdHash(String.valueOf(idSum).getBytes("UTF-8")) == hashSum))
                return true;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getIdSum() {
        return idSum;
    }

    public void setIdSum(int idSum) {
        this.idSum = idSum;
    }

    public int getHashSum() {
        return hashSum;
    }

    public void setHashSum(int hashSum) {
        this.hashSum = hashSum;
    }
}
