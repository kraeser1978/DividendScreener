package Common;

import java.util.*;

import static Common.DivsCoreData.props;

public class Stocks implements Comparable <Stocks>{
    public String companyName;
    public String ticker;
    public Double lastPrice;
    public Double yield;
    public Double pe;
    public LinkedHashMap<String,String> criteriaExecutionStatuses = new LinkedHashMap<>();

    public Stocks(){
        setDefaultExecutionStatus();
    }

    public int compareTo(Stocks o) {
        return this.getYield().compareTo(o.getYield());
    }

    public int calcCountOfPassedTests(){
        int count = 0;
        for (Map.Entry<String, String> entry : criteriaExecutionStatuses.entrySet()){
            String status = entry.getValue();
            if (status.equals(props.testPassed())) count++;
        }
        return count;
    }

    public void setDefaultExecutionStatus() {
        boolean isMapEmpty = criteriaExecutionStatuses.isEmpty();
        if (isMapEmpty){
            criteriaExecutionStatuses.put("Yrs", props.testPassed());
            criteriaExecutionStatuses.put("Yield", props.testPassed());
            criteriaExecutionStatuses.put("Year", props.testPassed());
            criteriaExecutionStatuses.put("Inc.", props.testPassed());
            criteriaExecutionStatuses.put("Ex-Div", props.testPassed());
            criteriaExecutionStatuses.put("Payout", props.testPassed());
            criteriaExecutionStatuses.put("($Mil)", props.testPassed());
            criteriaExecutionStatuses.put("P/E", props.testPassed());
            criteriaExecutionStatuses.put("SDYCheck", props.notTested());
            criteriaExecutionStatuses.put("DivCheck", props.notTested());
            criteriaExecutionStatuses.put("IncomeCheck", props.notTested());
        }
    }

    public String getCriteriaExecutionStatus(String criteriaName){
        return criteriaExecutionStatuses.get(criteriaName);
    }

    public void changeCriteriaExecutionStatus(String criteriaName, String newStatus){
        criteriaExecutionStatuses.put(criteriaName,newStatus);
    }

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

    public void setPE(Double pe){
        this.pe = pe;
    }

    public String getCompanyName(){
        return this.companyName;
    }

    public String getTicker(){
        return this.ticker;
    }

    public Double getLastPrice(){
        return this.lastPrice;
    }

    public Double getYield(){
        return this.yield;
    }

    public Double getPE(){return this.pe; }
}
