package cn.sqa2.nacams.admin;

import cn.sqa2.nacams.common.ApiException;
import org.apache.commons.csv.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class TabularReader {
    public record Row(String sheet,int number,Map<String,Object> values){}
    public List<Row> read(MultipartFile file){
        if(file.isEmpty()||file.getSize()>10L*1024*1024)throw new ApiException(422,"导入文件为空或超过10MB");
        String name=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);var result=new ArrayList<Row>();
        try{
            if(name.endsWith(".csv")){
                String text=new String(file.getBytes(),StandardCharsets.UTF_8);if(text.startsWith("\uFEFF"))text=text.substring(1);
                try(var parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).get().parse(new StringReader(text))){
                    for(CSVRecord record:parser){var p=new LinkedHashMap<String,Object>();record.toMap().forEach((k,v)->{if(!k.isBlank())p.put(k.trim(),v.trim());});
                        result.add(new Row("CSV",(int)record.getRecordNumber()+1,p));if(result.size()>10000)throw new ApiException(422,"每次导入最多10000行");}
                }
            }else if(name.endsWith(".xlsx")){
                try(var workbook=WorkbookFactory.create(file.getInputStream())){
                    var format=new DataFormatter(Locale.ROOT);
                    for(Sheet sheet:workbook){RowHeader headers=header(sheet,format);if(headers==null)continue;
                        for(int i=headers.index+1;i<=sheet.getLastRowNum();i++){
                            org.apache.poi.ss.usermodel.Row row=sheet.getRow(i);if(row==null)continue;var p=new LinkedHashMap<String,Object>();
                            for(var h:headers.names.entrySet()){
                                Cell cell=row.getCell(h.getKey());String value="";
                                CellType cellType=cell==null?CellType.BLANK:cell.getCellType();
                                // 只读已缓存的计算结果，绝不执行表格中的公式或外部链接。
                                if(cellType==CellType.FORMULA)cellType=cell.getCachedFormulaResultType();
                                if(cellType==CellType.ERROR)throw new ApiException(422,"公式缓存为错误值，请先在Excel核对并另存");
                                if(cell!=null && cellType==CellType.NUMERIC){
                                    if(DateUtil.isCellDateFormatted(cell))value=cell.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ISO_DATE);
                                    else value=java.math.BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                                }else if(cell!=null && cellType==CellType.STRING)value=cell.getStringCellValue().trim();
                                else if(cell!=null && cellType==CellType.BOOLEAN)value=Boolean.toString(cell.getBooleanCellValue());
                                p.put(h.getValue(),value);
                            }
                            if(p.values().stream().anyMatch(v->!v.toString().isBlank()))result.add(new Row(sheet.getSheetName(),i+1,p));
                            if(result.size()>10000)throw new ApiException(422,"每次导入最多10000行");
                        }
                    }
                }
            }else throw new ApiException(422,"只支持UTF-8 CSV或XLSX");
        }catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(422,"无法读取表格，请检查格式、表头与编码");}
        if(result.isEmpty())throw new ApiException(422,"文件没有数据行");return result;
    }
    private record RowHeader(int index,Map<Integer,String> names){}
    private RowHeader header(Sheet sheet,DataFormatter format){
        for(var row:sheet){var headers=new LinkedHashMap<Integer,String>();var seen=new HashSet<String>();
            for(Cell cell:row){String h=format.formatCellValue(cell).trim();if(!h.isBlank()){
                if(!seen.add(h))throw new ApiException(422,"表头重复："+h);headers.put(cell.getColumnIndex(),h);}}
            if(!headers.isEmpty())return new RowHeader(row.getRowNum(),headers);
        }return null;
    }
}
