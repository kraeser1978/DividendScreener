package Common;

import java.util.function.DoubleBinaryOperator;

public class GrowthStock {
    public String ticker;
    public String companyName;
    public Long companyCapValue;
    public Long tradeVolume;
    public Double threeMonthsGrowthRate;
    public Double oneYearGrowthRate;
    public Double threeYearsGrowthRate;

    public Long getCompanyCapValue(){
        return this.companyCapValue;
    }

    public Long getTradeVolume(){
        return this.tradeVolume;
    }

    public void setCompanyCapValue(Long capValue){
        this.companyCapValue = capValue;
    }

    public void setTradeVolume(Long tradeVolume){
        this.tradeVolume = tradeVolume;
    }

    public String getTicker(){
        return this.ticker;
    }

    public String getCompanyName(){
        return this.companyName;
    }

    public Double getThreeMonthsGrowthRate(){
        return this.threeMonthsGrowthRate;
    }

    public Double getOneYearGrowthRate(){
        return this.oneYearGrowthRate;
    }

    public Double getThreeYearsGrowthRate(){
        return this.threeYearsGrowthRate;
    }

    public void setTicker(String ticker){
        this.ticker = ticker;
    }

    public void setCompanyName(){
        this.companyName = companyName;
    }

    public void setThreeMonthsGrowthRate(Double rate){
        this.threeMonthsGrowthRate = rate;
    }

    public void setOneYearGrowthRate(Double rate){
        this.oneYearGrowthRate = rate;
    }

    public void setThreeYearsGrowthRate(Double rate){
        this.threeYearsGrowthRate = rate;
    }
}
