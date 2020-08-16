package Common;

public class Stocks {

    public String companyName;
    public String ticker;
    public Double lastPrice;
    public Double yield;

    public void setCompanyName(String companyName){
        this.companyName = companyName;
    }

    public void setTicker(String tickerCode){
        this.ticker = tickerCode;
    }

    public void setLastPrice(Double price){
        this.lastPrice = price;
    }

    public void setYield(Double yield){
        this.yield = yield;
    }

    public String getCompanyName(){
        return this.companyName;
    }

    public String getTicker(){
        return this.ticker;
    }

    public Double getLastPrice(){
        return  this.lastPrice;
    }

    public Double getYield(){
        return this.yield;
    }
}
